package neoproject.publicInstance;

import java.io.Serializable;

public class DataPacket implements Serializable {
    public DataPacket(int realLen, byte[] enData) {
        this.enData = enData;
        this.realLen = realLen;
    }

    public int realLen;
    public byte[] enData;
}
