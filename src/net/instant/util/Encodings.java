package net.instant.util;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.regex.Pattern;

public final class Encodings {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private static final char[] BASE64 = ("ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
        "abcdefghijklmnopqrstuvwxyz0123456789+/").toCharArray();
    private static final Pattern VALID_BASE64 = Pattern.compile(
        "(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}[AEIMQUYcgkosw048]=|" +
        "[A-Za-z0-9+/][AQgw]==)?");
    private static final int[] BASE64_REV;

    static {
        BASE64_REV = new int[128];
        for (int i = 0; i < 128; i++) BASE64_REV[i] = -1;
        for (int i = 0; i < 64; i++) BASE64_REV[BASE64[i]] = i;
    }

    /* Avoid constructions */
    private Encodings() {}

    private static String removeWhitespace(String s) {
        return s.replaceAll("\\s", "");
    }
    private static int fromHexDigit(char c) {
        if ('0' <= c && c <= '9') {
            return c - '0';
        } else if ('A' <= c && c <= 'F') {
            return c - 'A' + 10;
        } else if ('a' <= c && c <= 'f') {
            return c - 'a' + 10;
        } else {
            throw new IllegalArgumentException("Bad hex digit: " + c);
        }
    }
    private static int fromBase64Digit(char c) {
        if (c >= 128)
            throw new IllegalArgumentException("Bad base64 digit: " + c);
        int ret = BASE64_REV[c];
        if (ret == -1)
            throw new IllegalArgumentException("Bad base64 digit: " + c);
        return ret;
    }

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
        char[] ret = new char[2 * buf.length];
        int p = 0;
        for (int i = 0; i < buf.length; i++) {
            ret[p++] = HEX[buf[i] >> 4 & 0x0F];
            ret[p++] = HEX[buf[i] & 0x0F];
        }
        return new String(ret);
    }
    public static byte[] fromHex(String data) {
        data = removeWhitespace(data);
        if (data.length() % 2 != 0)
            throw new IllegalArgumentException("Hex string has odd amount " +
                "of digits");
        byte[] ret = new byte[data.length() / 2];
        int p = 0;
        for (int i = 0; i < data.length(); i += 2) {
            ret[p++] = (byte) (fromHexDigit(data.charAt(i)) << 4 |
                               fromHexDigit(data.charAt(i + 1)));
        }
        return ret;
    }

    public static String toBase64(byte[] buf) {
        char[] ret = new char[(buf.length + 2) / 3 * 4];
        int i, mi = buf.length / 3 * 3, p = 0;
        for (i = 0; i < mi; i += 3) {
            ret[p++] = BASE64[                       buf[i  ] >> 2 & 0x3F];
            ret[p++] = BASE64[buf[i  ] << 4 & 0x30 | buf[i+1] >> 4 & 0x0F];
            ret[p++] = BASE64[buf[i+1] << 2 & 0x3C | buf[i+2] >> 6 & 0x03];
            ret[p++] = BASE64[buf[i+2]      & 0x3F                       ];
        }
        switch (buf.length - i) {
            case 1:
                ret[p++] = BASE64[buf[i] >> 2 & 0x3F];
                ret[p++] = BASE64[buf[i] << 4 & 0x30];
                ret[p++] = '=';
                ret[p++] = '=';
                break;
            case 2:
                ret[p++] = BASE64[                     buf[i  ]>>2 & 0x3F];
                ret[p++] = BASE64[buf[i  ]<<4 & 0x30 | buf[i+1]>>4 & 0x0F];
                ret[p++] = BASE64[buf[i+1]<<2 & 0x3C                     ];
                ret[p++] = '=';
                break;
        }
        return new String(ret);
    }
    public static byte[] fromBase64(String data, boolean pad) {
        data = removeWhitespace(data);
        if (! pad) {
            switch (data.length() % 4) {
                case 2:
                    data += "==";
                    break;
                case 3:
                    data += "=";
                    break;
            }
        }
        if (! VALID_BASE64.matcher(data).matches())
            throw new IllegalArgumentException("Base64 string invalid");
        int l = data.length(), rl = l / 4 * 3;
        byte[] ret;
        if (l == 0) {
            ret = new byte[0];
        } else if (data.charAt(l - 2) == '=') {
            int d0 = fromBase64Digit(data.charAt(l - 4)),
                d1 = fromBase64Digit(data.charAt(l - 3));
            ret = new byte[rl - 2];
            ret[rl - 3] = (byte) (d0 << 2 | d1 >> 4);
            l -= 4;
        } else if (data.charAt(l - 1) == '=') {
            int d0 = fromBase64Digit(data.charAt(l - 4)),
                d1 = fromBase64Digit(data.charAt(l - 3)),
                d2 = fromBase64Digit(data.charAt(l - 2));
            ret = new byte[rl - 1];
            ret[rl - 3] = (byte) (d0 << 2 | d1 >> 4);
            ret[rl - 2] = (byte) (d1 << 4 | d2 >> 2);
            l -= 4;
        } else {
            ret = new byte[rl];
        }
        int p = 0;
        for (int i = 0; i < l; i += 4) {
            int d0 = fromBase64Digit(data.charAt(i    )),
                d1 = fromBase64Digit(data.charAt(i + 1)),
                d2 = fromBase64Digit(data.charAt(i + 2)),
                d3 = fromBase64Digit(data.charAt(i + 3));
            ret[p++] = (byte) (d0 << 2 | d1 >> 4);
            ret[p++] = (byte) (d1 << 4 | d2 >> 2);
            ret[p++] = (byte) (d2 << 6 | d3     );
        }
        return ret;
    }
    public static String toBase64(byte[] data, boolean pad) {
        String ret = toBase64(data);
        if (! pad) {
            int rl = ret.length();
            if (rl == 0) {
                /* NOP */
            } else if (ret.charAt(rl - 2) == '=') {
                return ret.substring(rl - 2);
            } else if (ret.charAt(rl - 1) == '=') {
                return ret.substring(rl - 1);
            }
        }
        return ret;
    }
    public static byte[] fromBase64(String data) {
        return fromBase64(data, true);
    }

}
