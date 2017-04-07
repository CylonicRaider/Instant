package net.instant.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.instant.api.Utilities;
import org.json.JSONObject;

public final class Util {

    public static class HeaderEntry extends LinkedHashMap<String, String> {

        private String value;

        public HeaderEntry(String value, Map<String, String> attrs) {
            super(attrs);
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }
        public void setValue(String value) {
            this.value = value;
        }

        public static HeaderEntry fromParts(String name, String value) {
            Map<String, String> attrs;
            if (value == null) {
                attrs = Collections.emptyMap();
            } else {
                attrs = parseTokenMap(value);
                if (attrs == null) return null;
            }
            return new HeaderEntry(name, attrs);
        }

    }

    public static final int BUFFER_SIZE = 16384;

    public static final Pattern CLIST_ITEM = Pattern.compile(
        "\\s*(?:([^,\"\\s]+)|\"((?:[^\\\\\"]|\\\\.)*)\")\\s*(,|$)");

    public static final Pattern HTTP_SPEC = Pattern.compile(
        "[\0-\040\177()<>@,;:\\\\\"/\\[\\]?={}]");
    public static final Pattern HTTP_TOKEN = Pattern.compile(
        "[^\0-\040\177()<>@,;:\\\\\"/\\[\\]?={}]+");
    public static final Pattern HTTP_QSTRING = Pattern.compile(
        "\"((?:[^\"]|\\\\.)*)\"");
    public static final Pattern HTTP_PARAM = Pattern.compile(String.format(
        "\\s*(?<pname>%s)\\s*=\\s*(?<pvalue>%<s|%s)\\s*(?<pend>;|$)",
        HTTP_TOKEN.pattern(), HTTP_QSTRING.pattern()));
    public static final Pattern HTTP_HEADERVALUE = Pattern.compile(
        String.format("\\s*(?<hvalue>[^;,]+)\\s*(;(?<hattrs>(%s)+))?\\s*" +
        "(?<hend>,|$)", HTTP_PARAM.pattern().replace(";|$", ";|,|$")));

    private static final SimpleDateFormat HTTP_FORMAT;

    private static final SecureRandom RNG = new SecureRandom();

    static {
        HTTP_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",
                                           Locale.ROOT);
        HTTP_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private Util() {}

    public static byte[] getRandomness(int len) {
        byte[] buf = new byte[len];
        RNG.nextBytes(buf);
        return buf;
    }
    public static void clear(byte[] arr) {
        for (int i = 0; i < arr.length; i++) arr[i] = 0;
    }

    public static String escapeJSString(String s, boolean full) {
        return Utilities.escapeStringJS(s, full);
    }
    public static String escapeJSString(String s) {
        return escapeJSString(s, false);
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

    public static List<String> parseCommaList(String list) {
        if (list == null) return null;
        List<String> ret = new ArrayList<String>();
        Matcher m = CLIST_ITEM.matcher(list);
        int lastIdx = 0;
        for (;;) {
            if (! m.find() || m.start() != lastIdx) return null;
            String value = m.group(1), qval = m.group(2), end = m.group(3);
            if (value != null) {
                ret.add(value);
            } else {
                ret.add(qval.replaceAll("\\\\(.)", "$1"));
            }
            lastIdx = m.end();
            if (end.isEmpty()) break;
        }
        return ret;
    }

    public static List<HeaderEntry> parseHTTPHeader(String value) {
        if (value == null) return null;
        List<HeaderEntry> ret = new ArrayList<HeaderEntry>();
        Matcher m = HTTP_HEADERVALUE.matcher(value);
        int lastIdx = 0;
        for (;;) {
            if (! m.find() || m.start() != lastIdx) return null;
            String val = m.group("hvalue"), attrs = m.group("hattrs");
            String end = m.group("hend");
            ret.add(HeaderEntry.fromParts(val, attrs));
            lastIdx = m.end();
            if (end.isEmpty()) break;
        }
        return ret;
    }
    public static Map<String, String> parseTokenMap(String list) {
        if (list == null) return null;
        Map<String, String> ret = new LinkedHashMap<String, String>();
        Matcher m = HTTP_PARAM.matcher(list);
        int lastIdx = 0;
        for (;;) {
            if (! m.find() || m.start() != lastIdx) return null;
            String name = m.group("pname"), value = m.group("pvalue");
            String end = m.group("pend");
            if (value.startsWith("\""))
                value = m.group(3).replaceAll("\\\\(.)", "$1");
            ret.put(name, value);
            lastIdx = m.end();
            if (end.isEmpty()) break;
        }
        return ret;
    }

    public static Map<String, String> parseQueryString(String query) {
        Map<String, String> ret = new LinkedHashMap<String, String>();
        for (String entry : query.split("&")) {
            String[] parts = entry.split("=", 2);
            String key, value;
            try {
                key = URLDecoder.decode(parts[0], "utf-8");
                if (parts.length == 1) {
                    value = null;
                } else {
                    value = URLDecoder.decode(parts[1], "utf-8");
                }
            } catch (UnsupportedEncodingException exc) {
                // Should not happen.
                throw new RuntimeException(exc);
            }
            ret.put(key, value);
        }
        return ret;
    }

    public static JSONObject createJSONObject(Object... params) {
        return Utilities.createJSONObject(params);
    }

    public static void mergeJSONObjects(JSONObject base, JSONObject add) {
        Utilities.mergeJSONObjects(base, add);
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

    public static boolean nonempty(String input) {
        return Utilities.nonempty(input);
    }

    public static boolean isTrue(String input) {
        return Utilities.isTrue(input);
    }

    public static String getConfiguration(String propName, boolean ex) {
        String ret = System.getProperty(propName);
        if (ret == null)
            ret = System.getenv(
                propName.toUpperCase().replace(".", "_"));
        if (! ex && ret != null && ret.isEmpty())
            ret = null;
        return ret;
    }
    public static String getConfiguration(String propName) {
        return getConfiguration(propName, false);
    }

}
