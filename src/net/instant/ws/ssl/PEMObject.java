package net.instant.ws.ssl;

import java.nio.ByteBuffer;
import net.instant.util.Util;

public class PEMObject {

    private final String label;
    private final ByteBuffer data;

    public PEMObject(String label, byte[] data) {
        this.label = label;
        this.data = ByteBuffer.wrap(data);
    }

    public String getLabel() {
        return label;
    }

    public ByteBuffer getData() {
        return data.asReadOnlyBuffer();
    }

    public byte[] getDataBytes() {
        // The getData() call avoids changing the underlying buffer's
        // position.
        return Util.extractBytes(getData());
    }

}
