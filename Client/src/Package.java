import java.io.Serializable;

public class Package implements Serializable {
    private int maxSize = 1324;

    private int seq;
    private boolean ack;
    private boolean end;
    private String content;
    private short checkSum;

    public Package(int seq, boolean ack,boolean end, String content) {
        this.seq = seq;
        this.ack = ack;
        this.end=end;
        this.content = content;
        //TODO
        this.checkSum = (short) (seq + content.length());
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

    public String getContent() {
        return content;
    }

    public boolean isCorrupted() {
        return false;
    }


}
