package net.instant.ws.ssl;

import java.nio.ByteBuffer;
import net.instant.util.Encodings;
import net.instant.util.Util;

public class PEMObject {

    private final String label;
    private final ByteBuffer data;

    public PEMObject(String label, byte[] data) {
        this.label = label;
        this.data = ByteBuffer.wrap(data);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN");
        if (! label.isEmpty()) sb.append(" ").append(label);
        sb.append("-----\n");
        String base64 = Encodings.toBase64(getDataBytes());
        int len = base64.length();
        for (int idx = 0; idx < len; idx += 64) {
            sb.append(base64.substring(idx, Math.min(idx + 64, len)))
                .append('\n');
        }
        sb.append("-----END");
        if (! label.isEmpty()) sb.append(" ").append(label);
        sb.append("-----");
        return sb.toString();
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
