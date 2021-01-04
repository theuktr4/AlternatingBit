import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class FileReceiver {
    private FileOutputStream fos;
    private final double pBitfehler;
    private final double pLoss;
    private final double pDuplicate;
    private static final int SIZE = 1400;
    private int timeout;
    private int oncethrough;
    long byteReceived;

    enum State {
        WAIT_GET_0, WAIT_GET_1, LAST_ACK_SENT, END
    }

    ;

    enum Trans {
        SEQ_0, SEQ_0_END, SEQ_1, SEQ_1_END, CORRUPT, RESEND_LAST, TIMEOUT
    }

    State currentState;
    Transition[][] transition;
    Package rcvpkg = null;
    Package sndpkg = null;

    public FileReceiver(double pBitfehler, double pLoss, double pDuplicate) {
        this.byteReceived = 0;
        this.pBitfehler = pBitfehler;
        this.pLoss = pLoss;
        this.pDuplicate = pDuplicate;
        this.timeout = 30000;
        String path = "C:\\Users\\Lukas\\IdeaProjects\\AlternatingBit\\Server\\ressources\\alf.webp";
        currentState = State.WAIT_GET_0;
        transition = new Transition[State.values().length][Trans.values().length];
        transition[State.WAIT_GET_0.ordinal()][Trans.SEQ_0.ordinal()] = new ONE();
        transition[State.WAIT_GET_0.ordinal()][Trans.SEQ_1.ordinal()] = new FIVE();
        transition[State.WAIT_GET_0.ordinal()][Trans.SEQ_0_END.ordinal()] = new LASTP();
        transition[State.WAIT_GET_0.ordinal()][Trans.CORRUPT.ordinal()] = new SIX();
        transition[State.WAIT_GET_1.ordinal()][Trans.SEQ_1.ordinal()] = new FOUR();
        transition[State.WAIT_GET_1.ordinal()][Trans.SEQ_0.ordinal()] = new THREE();
        transition[State.WAIT_GET_1.ordinal()][Trans.CORRUPT.ordinal()] = new TWO();
        transition[State.WAIT_GET_1.ordinal()][Trans.SEQ_1_END.ordinal()] = new LASTP();
        transition[State.LAST_ACK_SENT.ordinal()][Trans.TIMEOUT.ordinal()] = new END();
        transition[State.LAST_ACK_SENT.ordinal()][Trans.RESEND_LAST.ordinal()] = new RESENDLAST();

        //start
        oncethrough = 0;
        //get package
        try {
            fos = new FileOutputStream(path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        receive();
        //make sure last ack was successfull
        this.timeout = 500;
        while (currentState != State.END) {
            rdt_rcv();
            if (currentState != State.END) {
                processTransition(Trans.RESEND_LAST);
            }
        }

    }

    public void receive() {
        long start = 0;
        while (oncethrough == 0 || currentState != State.LAST_ACK_SENT) {
            byte[] data = rdt_rcv().getData();
            start = start == 0 ? System.currentTimeMillis() : start;
            if (isCorrupted(data)) {
                processTransition(Trans.CORRUPT);
            } else {
                rcvpkg = serializePacket(removeChecksum(data));
                if (rcvpkg.getSeq() == 0) {
                    if (rcvpkg.isEnd()) {
                        processTransition(Trans.SEQ_0_END);
                    } else {
                        processTransition(Trans.SEQ_0);
                    }
                } else {
                    if (rcvpkg.isEnd()) {
                        processTransition(Trans.SEQ_1_END);
                    } else {
                        processTransition(Trans.SEQ_1);
                    }

                }
            }
        }
        System.out.printf("%nGoodput: %.2f kB/s%n", (double) this.byteReceived / (System.currentTimeMillis() - start));
        try {
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public DatagramPacket rdt_rcv() {
        DatagramPacket rcvpkg = null;
        int THIS_PORT = 9000;
        try (DatagramSocket datagramSocket = new DatagramSocket(THIS_PORT)) {
            DatagramPacket p = new DatagramPacket(new byte[SIZE + 8], SIZE + 8);
            datagramSocket.setSoTimeout(timeout);
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
        //System.out.println(checksum==crc32.getValue());
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

    public void udt_send(Package sndpkt) {
        System.out.println("Sending packet with: " + sndpkt.getSeq());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream os = new ObjectOutputStream(outputStream);
            os.writeObject(sndpkg);
            os.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] content = outputStream.toByteArray();
        byte[] data = Arrays.copyOfRange(content, 0, SIZE);
        data = addChecksum(data);
        try (DatagramSocket socket = new DatagramSocket()) {
            //DatagramPacket sendP = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), this.TARGET_PORT);
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
        int TARGET_PORT = 9001;
        if (pLoss > 0.0 && new Random().nextInt((int) (1 / pLoss)) == 0) {
            System.err.println("Verworfen");
            return;
        }
        //dupliziert
        else if (pDuplicate > 0.0 && new Random().nextInt((int) (1 / pDuplicate) + 1) == 0) {
            System.err.println("Duplikat");
            DatagramPacket sendP = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), TARGET_PORT);
            socket.send(sendP);
            socket.send(sendP);
        }
        //Bitfehler
        else if (pBitfehler > 0.0 && new Random().nextInt((int) (1 / pBitfehler) + 1) == 0) {
            System.err.println("Bitfehler");
            int rdmByte = new Random().nextInt(data.length);
            if (data[rdmByte] == 1) {
                data[rdmByte] = 0;
            } else {
                data[rdmByte] = 1;
            }
            DatagramPacket sendP = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), TARGET_PORT);
            socket.send(sendP);
        } else {
            DatagramPacket sendP = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), TARGET_PORT);
            socket.send(sendP);
        }
    }

    public void deliverData() {
        try {
            this.byteReceived += this.rcvpkg.getContent().length;
            fos.write(this.rcvpkg.getContent());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private Package serializePacket(byte[] data) {
        Package res = null;
        try (ByteArrayInputStream in = new ByteArrayInputStream(data);
             ObjectInputStream is = new ObjectInputStream(in);) {
            res = (Package) is.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return res;
    }

    public void processTransition(Trans input) {
        Transition trans = transition[currentState.ordinal()][input.ordinal()];
        System.out.println(currentState + " transition: " + input);
        if (trans != null) {
            currentState = trans.execute(input);
        }
        System.out.println("new State: " + this.currentState);
    }

    public static void main(String[] args) {
        new FileReceiver(0.1, 0.05, 0.1);
    }

    abstract class Transition {
        abstract public FileReceiver.State execute(FileReceiver.Trans input);
    }

    class ONE extends Transition {
        @Override
        public FileReceiver.State execute(FileReceiver.Trans input) {
            deliverData();
            //make
            sndpkg = new Package(0, true, false, new byte[0]);
            udt_send(sndpkg);
            oncethrough = 1;
            return State.WAIT_GET_1;
        }

    }

    class TWO extends Transition {
        @Override
        public FileReceiver.State execute(FileReceiver.Trans input) {
            udt_send(sndpkg);
            return State.WAIT_GET_1;
        }

    }

    class THREE extends Transition {
        @Override
        public FileReceiver.State execute(FileReceiver.Trans input) {
            udt_send(sndpkg);
            rcvpkg = null;
            return State.WAIT_GET_1;
        }

    }

    class FOUR extends Transition {
        @Override
        public FileReceiver.State execute(FileReceiver.Trans input) {
            deliverData();
            //make
            sndpkg = new Package(1, true, false, new byte[0]);
            udt_send(sndpkg);
            return State.WAIT_GET_0;
        }

    }

    class FIVE extends Transition {
        @Override
        public FileReceiver.State execute(FileReceiver.Trans input) {
            if (oncethrough == 1) {
                rcvpkg = null;
                udt_send(sndpkg);
            }
            return State.WAIT_GET_0;
        }

    }

    class SIX extends Transition {
        @Override
        public FileReceiver.State execute(FileReceiver.Trans input) {
            if (oncethrough == 1) {
                udt_send(sndpkg);
            }
            return State.WAIT_GET_0;
        }

    }

    class LASTP extends Transition {
        @Override
        public FileReceiver.State execute(FileReceiver.Trans input) {
            deliverData();
            sndpkg = new Package(rcvpkg.getSeq(), true, true, new byte[0]);
            udt_send(sndpkg);
            return State.LAST_ACK_SENT;
        }

    }

    class RESENDLAST extends Transition {
        @Override
        public FileReceiver.State execute(FileReceiver.Trans input) {
            udt_send(sndpkg);
            return State.LAST_ACK_SENT;
        }

    }

    class END extends Transition {
        @Override
        public FileReceiver.State execute(FileReceiver.Trans input) {
            return State.END;
        }

    }


}
