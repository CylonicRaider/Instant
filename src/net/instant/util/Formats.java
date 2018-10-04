package net.instant.util;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.instant.api.RequestResponseData;
import net.instant.api.Utilities;

public final class Formats {

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

    public static final class HTTPLog {

        // Prevent construction,
        private HTTPLog() {}

        private static String prepare(String s) {
            return s.replace("\\", "\\x5C").replace("\r", "\\x0D")
                    .replace("\n", "\\x0A");
        }
        public static String escape(String s) {
            if (s == null || s.isEmpty()) return "-";
            return prepare(s).replace(" ", "\\x20");
        }
        public static String escapeInner(String s) {
            return prepare(s).replace(" ", "\\x20").replace("\"", "\\x22");
        }
        public static String quote(String s) {
            if (s == null) return "-";
            return '"' + prepare(s).replace("\"", "\\x22") + '"';
        }

        public static String formatAddress(InetSocketAddress a) {
            return (a == null) ? "-" : formatAddress(a.getAddress());
        }
        public static String formatAddress(InetAddress a) {
            return (a == null) ? "-" : escape(a.getHostAddress());
        }
        public static String formatDatetime(long ts) {
            // Obscure string formatting features FTW!
            return String.format((Locale) null, "[%td/%<tb/%<tY:%<tT %<tz]",
                                 ts);
        }
        public static String formatLength(long l) {
            return (l == -1) ? "-" : Long.toString(l);
        }

        public static String formatExtra(Map<String, Object> data) {
            if (data == null || data.isEmpty()) return null;
            StringBuilder sb = new StringBuilder("\"");
            for (Map.Entry<String, Object> ent : data.entrySet()) {
                if (sb.length() != 1) sb.append(' ');
                sb.append(escapeInner(ent.getKey()));
                if (ent.getValue() == null) continue;
                sb.append('=');
                sb.append(escapeInner(String.valueOf(ent.getValue())));
            }
            return sb.append("\"").toString();
        }

        public static String format(RequestResponseData req) {
            String ret = String.format("%s %s %s %s %s %s %s %s %s",
                formatAddress(req.getAddress()),
                escape(req.getRFC1413Identity()),
                escape(req.getAuthIdentity()),
                formatDatetime(req.getTimestamp()),
                quote(req.getMethod() + ' ' + req.getPath() + ' ' +
                      req.getHTTPVersion()),
                req.getStatusCode(),
                formatLength(req.getResponseLength()),
                quote(req.getReferrer()),
                quote(req.getUserAgent()));
            String extra = formatExtra(req.getExtraData());
            if (extra != null) ret += ' ' + extra;
            return ret;
        }

    }

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

    public static final Pattern INETSOCKETADDRESS = Pattern.compile(
        // According to RFCs 952 and 1123, host names may consist of ASCII
        // letters, digits, and hyphens. The IP address matching is
        // deliberately liberal.
        "(?<hostname>[a-zA-Z0-9.-]*|\\*)?(?:\\[(?<addr>[0-9a-fA-F.:]+)\\])?" +
        ":(?<port>[0-9]+)");

    private static final SimpleDateFormat HTTP_FORMAT;

    static {
        HTTP_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",
                                           Locale.ROOT);
        HTTP_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    // Prevent construction.
    private Formats() {}

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
        return Utilities.parseQueryString(query);
    }

    public static String formatHTTPLog(RequestResponseData req) {
        return HTTPLog.format(req);
    }

    public static String formatInetSocketAddress(InetSocketAddress addr,
                                                 boolean extended) {
        InetAddress baseAddr = addr.getAddress();
        if (extended && baseAddr.isAnyLocalAddress())
            return "*:" + addr.getPort();
        String hostname = baseAddr.getHostName();
        String hostaddr = baseAddr.getHostAddress();
        if (! extended || hostname.equals(hostaddr))
            return hostname + ":" + addr.getPort();
        return hostname + "[" + hostaddr + "]:" + addr.getPort();
    }
    public static String formatInetSocketAddress(InetSocketAddress addr) {
        return formatInetSocketAddress(addr, true);
    }
    public static InetSocketAddress parseInetSocketAddress(String addr) {
        Matcher m = INETSOCKETADDRESS.matcher(addr);
        if (! m.matches())
            throw new IllegalArgumentException("Invalid Internet socket " +
                "address: " + addr);
        int port;
        try {
            port = Integer.parseInt(m.group("port"));
        } catch (NumberFormatException exc) {
            throw new IllegalArgumentException("Invalid Internet socket " +
                "port: " + m.group("port"), exc);
        }
        if (m.group("addr") != null) {
            return new InetSocketAddress(m.group("addr"), port);
        } else if (m.group("hostname") == null) {
            throw new IllegalArgumentException("Internet socket address " +
                "has neither hostname nor host address: " + addr);
        } else if (m.group("hostname").equals("*")) {
            return new InetSocketAddress(port);
        } else {
            return new InetSocketAddress(m.group("hostname"), port);
        }
    }

}
