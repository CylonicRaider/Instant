package net.instant.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.bind.DatatypeConverter;
import org.json.JSONObject;

public final class Util {

    public static final int BUFFER_SIZE = 16384;

    public static final Pattern ESCAPE = Pattern.compile("[^ !#-\\[\\]-~]");

    public static final Pattern HTTP_SPEC = Pattern.compile(
        "[\0-\040\177()<>@,;:\\\\\"/\\[\\]?={}]");
    public static final Pattern HTTP_TOKEN = Pattern.compile(
        "[^\0-\040\177()<>@,;:\\\\\"/\\[\\]?={}]+");
    public static final Pattern HTTP_QSTRING = Pattern.compile(
        "\"((?:[^\"]|\\\\.)*)\"");
    public static final Pattern HTTP_PARAM = Pattern.compile(String.format(
        "\\s*(%s)\\s*=\\s*(%<s|%s)\\s*(;|$)", HTTP_TOKEN.pattern(),
        HTTP_QSTRING.pattern()));

    private static final SimpleDateFormat HTTP_FORMAT;

    private static final SecureRandom RNG = new SecureRandom();

    static {
        HTTP_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",
                                           Locale.ROOT);
        HTTP_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private Util() {}

    public static ByteBuffer toBytes(long l) {
        ByteBuffer ret = ByteBuffer.allocate(8);
        ret.putLong(l);
        ret.flip();
        return ret;
    }

    public static byte[] getRandomness(int len) {
        byte[] buf = new byte[len];
        RNG.nextBytes(buf);
        return buf;
    }
    public static void clear(byte[] arr) {
        for (int i = 0; i < arr.length; i++) arr[i] = 0;
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

    public static String escapeJSString(String s) {
        Matcher m = ESCAPE.matcher(s);
        // How old is *that* method to require a specific class?
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int ch = m.group().charAt(0);
            String repl = String.format((ch < 256) ? "\\x%02x" : "\\u%04x",
                                        ch);
            m.appendReplacement(sb, repl);
        }
        m.appendTail(sb);
        return sb.toString();
    }
    public static String escapeHttpString(String s) {
        Matcher m = HTTP_SPEC.matcher(s);
        if (! m.find()) return s;
        return '"' + m.replaceAll("\\\\$0") + '"';
    }

    public static String formatHttpTime(Calendar cal) {
        return HTTP_FORMAT.format(cal.getTime());
    }

    public static boolean matchWhitelist(String probe, List<Pattern> list) {
        for (Pattern p : list) {
            if (p.matcher(probe).matches()) return true;
        }
        return false;
    }

    public static Map<String, String> parseTokenMap(String list) {
        Map<String, String> ret = new LinkedHashMap<String, String>();
        Matcher m = HTTP_PARAM.matcher(list);
        int lastIdx = 0;
        for (;;) {
            if (! m.find() || m.start() != lastIdx) return null;
            String name = m.group(1), value = m.group(2), end = m.group(4);
            if (value.startsWith("\""))
                value = m.group(3).replaceAll("\\\\(.)", "$1");
            ret.put(name, value);
            lastIdx = m.end();
            if (end.isEmpty()) break;
        }
        return ret;
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

    public static byte[] extractBytes(ByteBuffer buf) {
        byte[] ret = new byte[buf.limit()];
        buf.get(ret);
        return ret;
    }

    public static String getConfiguration(String propName) {
        String ret = System.getProperty(propName);
        if (ret == null)
            ret = System.getenv(
                propName.toUpperCase().replaceAll("\\.", "_"));
        if (ret != null && ret.isEmpty())
            ret = null;
        return ret;
    }

}
