import java.io.Serializable;

public class Package implements Serializable {
    private int maxSize = 1324;

    private short seq;
    private byte ack;
    private String content;
    private short checkSum;

    public Package(short seq, byte ack, String content) {
        this.seq = seq;
        this.ack = ack;
        this.content = content;
        //TODO
        this.checkSum = (short) (seq + ack + content.length());
    }

    public int getSeq() {
        return seq;
    }

    public int getAck() {
        return ack;
    }

    public String getContent() {
        return content;
    }

    public int getCheckSum() {
        return checkSum;
    }


}
