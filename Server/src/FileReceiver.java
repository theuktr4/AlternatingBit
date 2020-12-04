import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class FileReceiver {

    private final int TARGET_PORT = 4712;
    private final int THIS_PORT = 4711;
    private static final int SIZE = 1400;
    private final int timeout = 20000;
    private int oncethrough;

    enum State {
        WAIT_GET_0, WAIT_GET_1
    }

    ;

    enum Trans {
        OK_0, OK_1, NOK_0, NOK_1
    }

    State currentState;
    Transition[][] transition;
    Package rcvpkg = null;
    Package sndpkg = null;

    public FileReceiver() {
        currentState = State.WAIT_GET_0;
        transition = new Transition[State.values().length][Trans.values().length];
        transition[State.WAIT_GET_0.ordinal()][Trans.OK_0.ordinal()] = new ONE();
        transition[State.WAIT_GET_0.ordinal()][Trans.NOK_0.ordinal()] = new FOUR();
        transition[State.WAIT_GET_1.ordinal()][Trans.OK_1.ordinal()] = new THREE();
        transition[State.WAIT_GET_1.ordinal()][Trans.NOK_1.ordinal()] = new TWO();
        //start
        oncethrough = 0;
        //get package
        receive();

    }

    public void receive() {
        while (oncethrough == 0 || !rcvpkg.isEnd()) {
            rcvpkg = rdt_rcv();
            if (!rcvpkg.isCorrupted()) {
                if (rcvpkg.getSeq() == 0) {
                    processTransition(Trans.OK_0);
                } else if (rcvpkg.getSeq() == 1) {
                    processTransition(Trans.OK_1);
                }
            } else {
                if (rcvpkg.getSeq() == 0) {
                    processTransition(Trans.NOK_0);
                } else if (rcvpkg.getSeq() == 1) {
                    processTransition(Trans.NOK_1);
                }
            }
        }
    }

    public Package rdt_rcv() {
        Package rcvpkg = null;
        try (DatagramSocket datagramSocket = new DatagramSocket(this.THIS_PORT)) {
            //datagramSocket.setSoTimeout(timeout);

            DatagramPacket p = new DatagramPacket(new byte[SIZE], SIZE);
            datagramSocket.receive(p);
            rcvpkg = serializePacket(p);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return rcvpkg;
    }

    public void udt_send() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream os = new ObjectOutputStream(outputStream);
            os.writeObject(this.sndpkg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] data = outputStream.toByteArray();
        try (DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket sendP = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), this.TARGET_PORT);
            socket.send(sendP);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deliverData() {
        System.out.println(this.rcvpkg.getContent());
    }

    public void processTransition(Trans input) {
        Transition trans = transition[currentState.ordinal()][input.ordinal()];
        System.out.println(currentState+" transition: "+input);
        if (trans != null) {
            currentState = trans.execute(input);
        }
        System.out.println("new State: "+this.currentState);
    }

    private Package serializePacket(DatagramPacket p) {
        byte[] data = p.getData();
        Package res = null;
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        try {
            ObjectInputStream is = new ObjectInputStream(in);
            res = (Package) is.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return res;
    }

    public static void main(String[] args) {
        new FileReceiver();
    }

    abstract class Transition {
        abstract public FileReceiver.State execute(FileReceiver.Trans input);
    }

    class ONE extends Transition {
        @Override
        public FileReceiver.State execute(FileReceiver.Trans input) {
            deliverData();
            //make
            sndpkg = new Package(0, true, false, "Hallo Welt");
            udt_send();
            oncethrough = 1;
            return State.WAIT_GET_1;
        }

    }

    class TWO extends Transition {
        @Override
        public FileReceiver.State execute(FileReceiver.Trans input) {
            udt_send();
            return State.WAIT_GET_1;
        }

    }

    class THREE extends Transition {
        @Override
        public FileReceiver.State execute(FileReceiver.Trans input) {
            deliverData();
            //make
            sndpkg = new Package(1, true, false, "Hallo Welt");
            udt_send();
            return State.WAIT_GET_0;
        }

    }

    class FOUR extends Transition {
        @Override
        public FileReceiver.State execute(FileReceiver.Trans input) {
            if (oncethrough == 1) {
                udt_send();
            }
            return State.WAIT_GET_0;
        }

    }


}
