import java.io.Serializable;

public class Package implements Serializable {
    private int seq;
    private boolean ack;
    private boolean end;
    private byte[] content;

    public Package(int seq, boolean ack,boolean end, byte[] content) {
        this.seq = seq;
        this.ack = ack;
        this.end=end;
        this.content = content;
    }

    public int getSeq() {
        return seq;
    }

    public boolean isAck() {
        return ack;
    }
    public boolean isEnd(){
        return end;
    }

    public byte[] getContent() {
        return content;
    }

    public boolean isCorrupted() {
        return false;
    }


}
