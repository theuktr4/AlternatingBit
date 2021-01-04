import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class FileSender {
    enum State {
        WAIT_SEND_0, WAIT_SEND_1, WAIT_ACK_0, WAIT_ACK_1, END
    }

    ;

    enum Trans {
        RDT_SEND, ACK_0, ACK_1, TIMEOUT, CORRUPT, ACK_0_END, ACK_1_END
    }

    private double pBitfehler;
    private double pLoss;
    private double pDuplicate;

    private State currentState;
    private Transition[][] transition;
    private int TARGET_PORT = 9000;
    private int THIS_PORT = 9001;
    private static final int SIZE = 1400;

    private String ip = "localhost";

    private int timeout;

    private Package sndpkt;

    String path = "C:\\Users\\Lukas\\IdeaProjects\\AlternatingBit\\Client\\ressources\\alf.webp";
    private File file = new File(path);
    private long ByteLeft;
    FileInputStream targetStream;

    private int sent = 0;

    public FileSender(int timeout, double pBitfehler, double pLoss, double pDuplicate) {
        this.pBitfehler = pBitfehler;
        this.pLoss = pLoss;
        this.pDuplicate = pDuplicate;
        this.timeout = timeout;
        currentState = State.WAIT_SEND_0;
        transition = new Transition[State.values().length][Trans.values().length];
        transition[State.WAIT_SEND_0.ordinal()][Trans.RDT_SEND.ordinal()] = new ONE();
        transition[State.WAIT_ACK_0.ordinal()][Trans.TIMEOUT.ordinal()] = new TWO();
        transition[State.WAIT_ACK_0.ordinal()][Trans.ACK_0.ordinal()] = new THREE();
        transition[State.WAIT_SEND_1.ordinal()][Trans.RDT_SEND.ordinal()] = new FOUR();
        transition[State.WAIT_ACK_1.ordinal()][Trans.TIMEOUT.ordinal()] = new FIVE();
        transition[State.WAIT_ACK_1.ordinal()][Trans.ACK_1.ordinal()] = new SIX();
        transition[State.WAIT_ACK_0.ordinal()][Trans.ACK_0_END.ordinal()] = new END();
        transition[State.WAIT_ACK_1.ordinal()][Trans.ACK_1_END.ordinal()] = new END();
        try {
            targetStream = new FileInputStream(file);
            ByteLeft = file.length();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //Start
        send();

    }


    public void send() {
        while (this.currentState != State.END) {
            //Ende
            processTransition(Trans.RDT_SEND);
            DatagramPacket dp = rdt_rcv();
            if (dp != null) {
                byte[] data = dp.getData();
                if (isCorrupted(data)) {
                    processTransition(Trans.CORRUPT);
                } else {
                    Package p = serializePacket(removeChecksum(data));
                    if (p.isAck() && p.getSeq() == 0) {
                        if (p.isEnd() && sndpkt.isEnd()) {
                            processTransition(Trans.ACK_0_END);
                        } else {
                            processTransition(Trans.ACK_0);
                        }
                    } else if (p.isAck() && p.getSeq() == 1) {
                        if (p.isEnd() && sndpkt.isEnd()) {
                            processTransition(Trans.ACK_1_END);
                        } else {
                            processTransition(Trans.ACK_1);
                        }

                    }
                }
            }

        }
        try {
            System.out.println(targetStream.available());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public Package makePackage(int seq) {
        Package res = null;
        int contentSize = SIZE - 100;
        boolean end = ByteLeft < contentSize;
        byte[] content = new byte[SIZE - 100];
        try {
            ByteLeft -= targetStream.read(content);
            res = new Package(seq, false, end, content);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }

    public DatagramPacket rdt_rcv() {
        DatagramPacket rcvpkg = null;
        try (DatagramSocket datagramSocket = new DatagramSocket(this.THIS_PORT)) {
            datagramSocket.setSoTimeout(this.timeout);
            DatagramPacket p = new DatagramPacket(new byte[SIZE + 8], SIZE + 8);
            datagramSocket.receive(p);
            rcvpkg = p;

        } catch (SocketTimeoutException end) {
            processTransition(Trans.TIMEOUT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rcvpkg;
    }

    boolean isCorrupted(byte[] data) {
        long checksum = 0;
        byte[] check = Arrays.copyOfRange(data, 0, 8);
        byte[] content = removeChecksum(data);
        Checksum crc32 = new CRC32();
        crc32.update(content, 0, content.length);
        System.out.println("Got " + crc32.getValue());
        try (ByteArrayInputStream bis = new ByteArrayInputStream(check); DataInputStream dis = new DataInputStream(bis)) {
            checksum = dis.readLong();
            System.out.println("Expected " + checksum);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return checksum != crc32.getValue();
    }

    byte[] removeChecksum(byte[] data) {
        return Arrays.copyOfRange(data, 8, data.length);

    }

    byte[] addChecksum(byte[] data) {
        byte[] res = new byte[data.length + 8];
        System.arraycopy(data, 0, res, 8, data.length);
        Checksum crc32 = new CRC32();
        crc32.update(data, 0, data.length);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(crc32.getValue());
        System.arraycopy(buffer.array(), 0, res, 0, 8);
        System.out.println("added checksum: " + crc32.getValue());
        return res;
    }

    void udt_send(Package sndpkt) {
        System.out.println("Sending packet with: " + sndpkt.getSeq());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream os = new ObjectOutputStream(outputStream);
            os.writeObject(sndpkt);
            os.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] content = outputStream.toByteArray();
        byte[] data = Arrays.copyOfRange(content, 0, SIZE);
        data = addChecksum(data);
        try (DatagramSocket socket = new DatagramSocket()) {
            //DatagramPacket sendP = new DatagramPacket(data, data.length, InetAddress.getByName(this.ip), this.TARGET_PORT);
            try {
                sendUnreliable(data, socket);
            } catch (IOException ex) {
                ex.printStackTrace();
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void sendUnreliable(byte[] data, DatagramSocket socket) throws IOException {
        //verworfen
        if (new Random().nextInt((int) (1 / pLoss) + 1) == 0) {
            System.err.println("Verworfen");
            return;
        }
        //dupliziert
        if (new Random().nextInt((int) (1 / pDuplicate) + 1) == 0) {
            System.err.println("Duplikat");
            DatagramPacket sendP = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), this.TARGET_PORT);
            socket.send(sendP);
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            socket.send(sendP);
        }
        //Bitfehler
        else if (new Random().nextInt((int) (1 / pBitfehler) + 1) == 0) {
            System.err.println("Bitfehler");
            int rdmByte = new Random().nextInt(data.length);
            if (data[rdmByte] == 1) {
                data[rdmByte] = 0;
            } else {
                data[rdmByte] = 1;
            }
            DatagramPacket sendP = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), this.TARGET_PORT);
            socket.send(sendP);
        } else {
            DatagramPacket sendP = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), this.TARGET_PORT);
            socket.send(sendP);
        }
    }


    private Package serializePacket(byte[] data) {
        Package res = null;
        try (ByteArrayInputStream in = new ByteArrayInputStream(data);
             ObjectInputStream is = new ObjectInputStream(in);) {
            res = (Package) is.readObject();
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
        new FileSender(50, 0.05, 0.1, 0.05);
    }

    abstract class Transition {
        abstract public FileSender.State execute(FileSender.Trans input);
    }

    class ONE extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            sndpkt = makePackage(0);
            udt_send(sndpkt);
            return State.WAIT_ACK_0;
        }

    }

    class TWO extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            udt_send(sndpkt);
            return State.WAIT_ACK_0;
        }

    }

    class THREE extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            return State.WAIT_SEND_1;
        }

    }

    class FOUR extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            sndpkt = makePackage(1);
            udt_send(sndpkt);
            return State.WAIT_ACK_1;
        }

    }

    class FIVE extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            udt_send(sndpkt);
            return State.WAIT_ACK_1;
        }

    }

    class SIX extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            return State.WAIT_SEND_0;
        }

    }

    class END extends Transition {
        @Override
        public FileSender.State execute(FileSender.Trans input) {
            return State.END;
        }

    }


}
