import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class FileSender {
    enum State {
        WAIT_SEND_0, WAIT_SEND_1, WAIT_ACK_0, WAIT_ACK_1,
    }

    ;

    enum Trans {
        RDT_SEND, ACK_0, ACK_1, TIMEOUT, COR
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

    private int curAck;
    private int seq;
    private int timeout;

    private Package sndpkt;

    private String stringToBeSend = "Wer während der Autofahrt über Handy oder Freisprechanlage telefoniert, fährt wie ein\n" +
            "angetrunkener Wagenlenker. Zu diesem Schluss kommen die Psychologen Frank Drews,\n" +
            "David Strayer und der Toxikologe Dennis Crouch von der Universität Utah in ihrer Studie,\n" +
            "die sie heute in dem Journal Human Factors veröffentlichen. 25 Männer und 15 Frauen im\n" +
            "Alter zwischen 22 und 34 Jahren nahmen an «A Comparison of the Cell Phone Driver and\n" +
            "the Drunk Driver» teil. Das Bundesamt für Luftfahrt finanzierte die Untersuchungen mit\n" +
            "25 000 Dollar, um Rückschlüsse auf die Aufmerksamkeit von Piloten ziehen zu können.\n" +
            "«Wenn Sie hinter dem Lenkrad telefonieren, fahren Sie, als ob Sie 0,8 Promille Alkohol\n" +
            "intus hätten», erklärt Frank Drews, Assistenz-Professor für Psychologie. Diese Blutalkoholkonzentration sei bereits in den meisten amerikanischen Staaten illegal. «Wenn der\n" +
            "Gesetzgeber wirklich das Autofahren sicherer machen möchte, sollte er das Telefonieren\n" +
            "komplett verbieten», meint Drews.\n" +
            "Sowohl Freisprechanlage als auch Handy beeinflussten den Fahrstil und zeigten keinen\n" +
            "Unterschied im Grad der Ablenkung. «Das stellt besonders die Auflagen in Frage, die das\n" +
            "Telefonieren mit Handys verbieten, es aber über Freisprechanlage erlauben.» Verglichen\n" +
            "mit konzentrierten Fahrern steuerten die telefonierenden Insassen ihr Gefährt in der Simulation etwas langsamer, bremsten später und benötigen mehr Zeit führ die Anfahrt danach. Durch das Auswerten aktueller und früherer Studien zeigen die Forscher, dass Telefonierende fünf Mal eher in einen Unfall verwickelt werden. Die gleiche\n" +
            "Wahrscheinlichkeit geben andere Studien für Fahrer mit 0,8 Promille Blutalkohol an. […] ";
    private byte[] toBeSend = stringToBeSend.getBytes();
    private File file = new File("C:\\Users\\Lukas\\IdeaProjects\\AlternatingBit\\Client\\src\\alf.webp");
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
        try {
            targetStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //Start
        send();

    }


    public void send() {
        while (stringToBeSend.length() > 0) {
            processTransition(Trans.RDT_SEND);
            DatagramPacket dp = rdt_rcv();
            if (dp != null) {
                byte[] data = dp.getData();
                if (isCorrupted(data)) {
                    processTransition(Trans.COR);
                } else {
                    Package p = serializePacket(removeChecksum(data));
                    if (p.isAck() && p.getSeq() == 0) {
                        processTransition(Trans.ACK_0);

                    } else if (p.isAck() && p.getSeq() == 1) {
                        processTransition(Trans.ACK_1);
                    }
                }
            }

        }

    }

    public Package makePackage(int seq) {
        Package res = null;
        try {
            byte[] content = new byte[SIZE - 100];

            if (targetStream.available() <= SIZE - 100) {
                for (int i = 0; i < targetStream.available(); i++) {
                    content[i] = (byte) targetStream.read();
                }

                res = new Package(seq, false, true, content);
                stringToBeSend = "";
            } else {
                for (int i = 0; i < SIZE - 100; i++) {
                    content[i] = (byte) targetStream.read();
                }
                res = new Package(seq, false, false, content);
            }
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
        if (new Random().nextInt((int)(1/pLoss)+1) == 0) {
            System.err.println("Verworfen");
            return;
        }
        //dupliziert
        if (new Random().nextInt((int) (1/pDuplicate)+1) == 0) {
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
        else if (new Random().nextInt((int) (1/pBitfehler)+1) == 0) {
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
        new FileSender(30, 0.05, 0.1, 0.05);
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


}
