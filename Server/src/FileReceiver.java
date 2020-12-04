import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

public class FileReceiver {

    private static final int SIZE = 1400;
    private final int PORT = 4711;
    private final int timeout = 20000;

    public FileReceiver() {
        try (DatagramSocket datagramSocket = new DatagramSocket(this.PORT)) {
            datagramSocket.setSoTimeout(timeout);
            while (true) {
                DatagramPacket p = new DatagramPacket(new byte[SIZE], SIZE);
                datagramSocket.receive(p);
                serializePacket(p);
            }

        } catch (SocketTimeoutException end) {
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void serializePacket(DatagramPacket p) {
        byte[] data = p.getData();
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        try {
            ObjectInputStream is = new ObjectInputStream(in);
            Package result = (Package) is.readObject();
            System.out.println("Erhalten: "+ result.getContent());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        new FileReceiver();
    }


}
