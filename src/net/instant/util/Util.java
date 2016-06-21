package net.instant.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.List;
import java.util.regex.Pattern;
import javax.xml.bind.DatatypeConverter;
import org.json.JSONObject;

public final class Util {

    public static final int BUFFER_SIZE = 16384;

    private static final SecureRandom rng = new SecureRandom();

    private Util() {}

    public static ByteBuffer toBytes(long l) {
        ByteBuffer ret = ByteBuffer.allocate(8);
        ret.putLong(l);
        ret.flip();
        return ret;
    }

    public static byte[] getRandomness(int len) {
        byte[] buf = new byte[len];
        rng.nextBytes(buf);
        return buf;
    }
    public static void clear(byte[] arr) {
        for (int i = 0; i < arr.length; i++) arr[i] = 0;
    }

    public static String toHex(byte[] buf) {
        return DatatypeConverter.printHexBinary(buf);
    }

    public static String toBase64(byte[] buf) {
        return DatatypeConverter.printBase64Binary(buf);
    }

    public static boolean matchWhitelist(String probe, List<Pattern> list) {
        for (Pattern p : list) {
            if (p.matcher(probe).matches()) return true;
        }
        return false;
    }

    public static JSONObject createJSONObject(Object... params) {
        if (params.length % 2 == 1)
            throw new IllegalArgumentException("Invalid parameter amount " +
                "for createObject()");
        JSONObject ret = new JSONObject();
        for (int i = 0; i < params.length; i += 2) {
            if (! (params[i] instanceof String))
                throw new IllegalArgumentException("Invalid parameter " +
                    "type for createObject()");
            ret.put((String) params[i], params[i + 1]);
        }
        return ret;
    }

    public static ByteBuffer readInputStream(InputStream input)
            throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        int idx = 0;
        for (;;) {
            int rd = input.read(buf, idx, buf.length - idx);
            if (rd < 0) break;
            idx += rd;
            if (idx == buf.length) {
                byte[] nbuf = new byte[idx * 2];
                System.arraycopy(buf, 0, nbuf, 0, idx);
                buf = nbuf;
            }
        }
        return ByteBuffer.wrap(buf, 0, idx);
    }
    public static ByteBuffer readInputStreamClosing(InputStream input)
            throws IOException {
        try {
            return readInputStream(input);
        } finally {
            input.close();
        }
    }

}
