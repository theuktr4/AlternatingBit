import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class FileSender {
    private int port = 4711;
    private String ip = "localhost";

    private int curAck;
    private int seq;
    public FileSender(){
        Package p = new Package((short) 1,(byte)0,"Hallo Welt");
        udt_send(p);

    }
    void udt_send(Package p){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream os = new ObjectOutputStream(outputStream);
            os.writeObject(p);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] data = outputStream.toByteArray();
        try (DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket sendP = new DatagramPacket(data, data.length,InetAddress.getByName(this.ip), this.port);
            socket.send(sendP);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new FileSender();
    }
}
