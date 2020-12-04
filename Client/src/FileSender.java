import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class FileSender {
    enum State {
        WAIT_SEND_0, WAIT_SEND_1, WAIT_ACK_0, WAIT_ACK_1,
    }

    ;

    enum Trans {
        RDT_SEND, RCV_0_OK, RCV_1_OK, RCV_0_NOK, RCV_1_NOK, TIMEOUT, IGNORE_ACK
    }

    private State currentState;
    private Transition[][] transition;
    private int TARGET_PORT = 9000;
    private int THIS_PORT = 9001;
    private static final int SIZE = 10;

    private String ip = "localhost";

    private int curAck;
    private int seq;
    private int timeout;

    private Package sndpkt;

    private String toBeSent = "fjadsfoewuroihjfkldasfhjdlskajflkasdureworiuweroijfdkalsfjadslkfuewoirquewifjdaslkfjler";
    private int sent = 0;

    public FileSender(int timeout) {
        this.timeout = timeout;
        currentState = State.WAIT_SEND_0;
        transition = new Transition[State.values().length][Trans.values().length];
        transition[State.WAIT_SEND_0.ordinal()][Trans.RDT_SEND.ordinal()] = new ONE();
        transition[State.WAIT_SEND_0.ordinal()][Trans.IGNORE_ACK.ordinal()] = new TEN();
        transition[State.WAIT_ACK_0.ordinal()][Trans.RCV_0_NOK.ordinal()] = new TWO();
        transition[State.WAIT_ACK_0.ordinal()][Trans.TIMEOUT.ordinal()] = new THREE();
        transition[State.WAIT_ACK_0.ordinal()][Trans.RCV_0_OK.ordinal()] = new FOUR();
        transition[State.WAIT_SEND_1.ordinal()][Trans.IGNORE_ACK.ordinal()] = new FIVE();
        transition[State.WAIT_SEND_1.ordinal()][Trans.RDT_SEND.ordinal()] = new SIX();
        transition[State.WAIT_ACK_1.ordinal()][Trans.RCV_1_NOK.ordinal()] = new SEVEN();
        transition[State.WAIT_ACK_1.ordinal()][Trans.TIMEOUT.ordinal()] = new EIGHT();
        transition[State.WAIT_ACK_1.ordinal()][Trans.RCV_1_OK.ordinal()] = new NINE();

        //Start
        send();

    }

    public void send() {
        while (toBeSent.length() > 0) {
            rdt_send();
        }
    }

    public void rdt_send() {
        processTransition(Trans.RDT_SEND);
        Package p = rdt_rcv();
        if (p != null) {
            if (!p.isCorrupted()) {
                if (p.getSeq() == 0) {
                    processTransition(Trans.RCV_0_OK);
                } else {
                    processTransition(Trans.RCV_1_OK);
                }
            } else {
                if (p.getSeq() == 0) {
                    processTransition(Trans.RCV_0_NOK);
                } else {
                    processTransition(Trans.RCV_1_NOK);
                }
            }
        }

    }

    public Package makePackage() {
        int seq = sent % 2 == 0 ? 0 : 1;
        Package res = null;
        if (toBeSent.length() < SIZE) {
            res = new Package(seq, false, true, toBeSent);
            toBeSent = "";
        } else {
            res = new Package(seq, false, false, toBeSent.substring(0, SIZE - 1));
            toBeSent = toBeSent.substring(SIZE);
        }
        sent++;
        return res;
    }

    public Package rdt_rcv() {
        Package rcvpkg = null;
        try (DatagramSocket datagramSocket = new DatagramSocket(this.THIS_PORT)) {
            datagramSocket.setSoTimeout(this.timeout);
            DatagramPacket p = new DatagramPacket(new byte[1024], 1024);
            datagramSocket.receive(p);
            //TODO
            //Prüfen und weiterverarbeiten
            rcvpkg = serializePacket(p);

        } catch (SocketTimeoutException end) {
            processTransition(Trans.TIMEOUT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rcvpkg;
    }

    void udt_send(Package sndpkt) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream os = new ObjectOutputStream(outputStream);
            os.writeObject(sndpkt);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] data = outputStream.toByteArray();
        try (DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket sendP = new DatagramPacket(data, data.length, InetAddress.getByName(this.ip), this.TARGET_PORT);
            socket.send(sendP);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private Package serializePacket(DatagramPacket p) {
        byte[] data = p.getData();
        Package res = null;
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        try {
            ObjectInputStream is = new ObjectInputStream(in);
            res = (Package) is.readObject();
            System.out.println("Erhalten: " + res.getContent());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return res;
    }

    public void processTransition(Trans input) {
        System.out.println(currentState + " transition: " + input);
        Transition trans = transition[currentState.ordinal()][input.ordinal()];
        if (trans != null) {
            currentState = trans.execute(input);
        }
        System.out.println("new State: " + this.currentState);
    }

    public static void main(String[] args) {
        new FileSender(20000);
    }

    abstract class Transition {
        abstract public FileSender.State execute(FileSender.Trans input);
    }

    class ONE extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            sndpkt = makePackage();
            udt_send(sndpkt);
            return State.WAIT_ACK_0;
        }

    }

    class TWO extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            return State.WAIT_ACK_0;
        }

    }

    class THREE extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            udt_send(sndpkt);
            return State.WAIT_ACK_0;
        }

    }

    class FOUR extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            return State.WAIT_SEND_1;
        }

    }

    class FIVE extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            return State.WAIT_SEND_1;
        }

    }

    class SIX extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            sndpkt = makePackage();
            udt_send(sndpkt);
            return State.WAIT_ACK_1;
        }

    }

    class SEVEN extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            return State.WAIT_ACK_1;
        }

    }

    class EIGHT extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            udt_send(sndpkt);
            return State.WAIT_ACK_1;
        }

    }

    class NINE extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            return State.WAIT_SEND_0;
        }

    }

    class TEN extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            return State.WAIT_SEND_0;
        }

    }


}
