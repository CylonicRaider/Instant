package net.instant.util;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import javax.xml.bind.DatatypeConverter;

public final class Encodings {

    /* Avoid constructions */
    private Encodings() {}

    public static ByteBuffer toBytes(long l) {
        ByteBuffer ret = ByteBuffer.allocate(8);
        ret.putLong(l);
        ret.flip();
        return ret;
    }
    public static ByteBuffer toBytes(String s) {
        byte[] ret;
        try {
            ret = s.getBytes("utf-8");
        } catch (UnsupportedEncodingException exc) {
            // Why must *this* be a checked one?
            throw new RuntimeException(exc);
        }
        return ByteBuffer.wrap(ret);
    }

    public static String toHex(byte[] buf) {
        return DatatypeConverter.printHexBinary(buf);
    }
    public static byte[] fromHex(String data) {
        return DatatypeConverter.parseHexBinary(data);
    }

    public static String toBase64(byte[] buf) {
        return DatatypeConverter.printBase64Binary(buf);
    }
    public static byte[] fromBase64(String data) {
        return DatatypeConverter.parseBase64Binary(data);
    }

}
