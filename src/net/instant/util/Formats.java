package net.instant.util;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
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
            boolean first = true;
            for (Map.Entry<String, Object> ent : data.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    sb.append(' ');
                }
                sb.append(escapeInner(ent.getKey()));
                if (ent.getValue() == null) continue;
                sb.append('=');
                sb.append(escapeInner(String.valueOf(ent.getValue())));
            }
            return sb.append("\"").toString();
        }

        public static String format(RequestResponseData req) {
            StringBuilder sb = new StringBuilder();
            sb.append(formatAddress(req.getAddress())).append(' ')
              .append(escape(req.getRFC1413Identity())).append(' ')
              .append(escape(req.getAuthIdentity())).append(' ')
              .append(formatDatetime(req.getTimestamp())).append(' ')
              .append(quote(req.getMethod() + ' ' + req.getPath() + ' ' +
                            req.getHTTPVersion())).append(' ')
              .append(req.getStatusCode()).append(' ')
              .append(formatLength(req.getResponseLength())).append(' ')
              .append(quote(req.getReferrer())).append(' ')
              .append(quote(req.getUserAgent()));
            String extra = formatExtra(req.getExtraData());
            if (extra != null) sb.append(' ').append(extra);
            return sb.toString();
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
        // deliberately liberal. Admissible characters for IPv6 scope ID-s
        // are taken from RFC 6874 (excluding the tilde).
        "(?<hostname>[a-zA-Z0-9.-]*|\\*)?(?:\\[(?<addr>[0-9a-fA-F.:]+" +
        "(?:%[0-9a-zA-Z._-]+)?)\\])?:(?<port>[0-9]+)");

    private static final Pattern IPV6_LONGEST_ZEROS = Pattern.compile(
        // RFC 5952 states that the longest run of (at least two) zeroes must
        // be abbreviated, with ties going to the leftmost one. This regex
        // selects a run of at least two zeroes if there exists no longer run
        // in the remainder of the string; assuming that every field in the
        // input string has leading zeroes stripped and that replaceFirst()
        // is used, this satisfies all requirements. The optional surrounding
        // colons are matched to simplify collapsing the matched sequence into
        // a double colon.
        ":?(0(?::0)+)(?!.*\\1:0):?");

    private static final SimpleDateFormat HTTP_FORMAT;

    public static final Pattern NARROW_ESCAPE_SEQUENCE = Pattern.compile(
        "\\\\(?:[abtnvfr]|[0-7]{1,2}|[0-3][0-7]{2}|x[0-9a-fA-F]{2}|" +
        "u[0-9a-fA-F]{4}|U[0-9a-fA-F]{8})");
    public static final Pattern ESCAPE_SEQUENCE = Pattern.compile(
        NARROW_ESCAPE_SEQUENCE.pattern() + "|\\\\[^0-7xuU]");

    private static final Pattern PATTERN_INTERESTING = Pattern.compile(
        "\\\\.|[\0-\37/\\[\\]\177-\237]");

    // formatPattern() (pattern parsing) states.
    private static final int FPS_NORMAL      = 0;
    private static final int FPS_CLASS       = 1;
    private static final int FPS_QUOTE       = 2;
    private static final int FPS_ENTER_QUOTE = 3;
    private static final int FPS_SKIP        = 4;

    private static final String[] ESCAPES;
    private static final String[] REV_ESCAPES;
    private static final boolean[] REGEX_ESCAPE;

    private static final Map<Integer, Character> REGEX_FLAGS;

    static {
        HTTP_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",
                                           Locale.ROOT);
        HTTP_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));

        ESCAPES = new String[16];
        ESCAPES['\0' ] = "\\\\0";
        ESCAPES['\7' ] = "\\\\a";
        ESCAPES['\b' ] = "\\\\b";
        ESCAPES['\t' ] = "\\\\t";
        ESCAPES['\n' ] = "\\\\n";
        ESCAPES['\13'] = "\\\\v";
        ESCAPES['\f' ] = "\\\\f";
        ESCAPES['\r' ] = "\\\\r";

        REV_ESCAPES = new String[128];
        REGEX_ESCAPE = new boolean[ESCAPES.length];
        for (char i = 0; i < ESCAPES.length; i++) {
            if (ESCAPES[i] == null) continue;
            REV_ESCAPES[ESCAPES[i].charAt(2)] = Character.toString(i);
            REGEX_ESCAPE[i] = true;
        }
        // \b and \v do not map to single characters inside patterns.
        REGEX_ESCAPE['\b' ] = false;
        REGEX_ESCAPE['\13'] = false;

        REGEX_FLAGS = new LinkedHashMap<Integer, Character>();
        REGEX_FLAGS.put(Pattern.CASE_INSENSITIVE,        'i');
        REGEX_FLAGS.put(Pattern.UNIX_LINES,              'd');
        REGEX_FLAGS.put(Pattern.MULTILINE,               'm');
        REGEX_FLAGS.put(Pattern.DOTALL,                  's');
        REGEX_FLAGS.put(Pattern.UNICODE_CASE,            'u');
        REGEX_FLAGS.put(Pattern.COMMENTS,                'x');
        REGEX_FLAGS.put(Pattern.UNICODE_CHARACTER_CLASS, 'U');
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
        synchronized (HTTP_FORMAT) {
            return HTTP_FORMAT.format(cal.getTime());
        }
    }
    public static String formatHttpTime(Date d) {
        synchronized (HTTP_FORMAT) {
            return HTTP_FORMAT.format(d);
        }
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

    private static String shortenIPv6(String addr) {
        // Avoid creating ambiguous addresses.
        if (addr.contains("::")) return addr;
        // FIXME: Relying on Java's interna to strip redundant zeroes for us.
        return IPV6_LONGEST_ZEROS.matcher(addr).replaceFirst("::");
    }
    public static String formatInetSocketAddress(InetSocketAddress addr,
                                                 boolean extended) {
        InetAddress baseAddr = addr.getAddress();
        if (extended && baseAddr != null && baseAddr.isAnyLocalAddress())
            return "*:" + addr.getPort();
        String hostname, hostaddr;
        if (baseAddr == null) {
            hostname = addr.getHostName();
            hostaddr = hostname;
        } else {
            hostname = baseAddr.getHostName();
            hostaddr = baseAddr.getHostAddress();
        }
        if (! extended || (hostname.equals(hostaddr) &&
                           ! hostname.contains(":")))
            return shortenIPv6(hostname) + ":" + addr.getPort();
        if (hostname.equals(hostaddr))
            hostname = "";
        return hostname + "[" + shortenIPv6(hostaddr) + "]:" + addr.getPort();
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

    public static String formatCharacter(int ch) {
        if (ch == '\'') {
            return "\"" + (char) ch + "\"";
        } else if (ch >= ' ' && ch <= '~') {
            return "'" + (char) ch + "'";
        } else if (ch < ' ' || ch >= '\177' && ch <= '\237') {
            return String.format("<U+%04X>", ch);
        } else {
            return String.format("'%c'(U+%04X)", ch, ch);
        }
    }

    public static String tryParseEscapeSequence(String input,
                                                String allowedSpecial,
                                                String allowedRaw) {
        if (! ESCAPE_SEQUENCE.matcher(input).matches()) return null;
        char selector = input.charAt(1);
        if (allowedSpecial == null ||
                allowedSpecial.indexOf(selector) != -1) {
            if (selector < REV_ESCAPES.length &&
                    REV_ESCAPES[selector] != null)
                return REV_ESCAPES[selector];
            switch (selector) {
                case 'x': case 'u':
                    return Character.toString((char) Integer.parseInt(
                        input.substring(2), 16));
                case 'U':
                    return new String(Character.toChars(Integer.parseInt(
                        input.substring(2), 16)));
            }
        }
        if (allowedRaw == null || allowedRaw.indexOf(selector) != -1) {
            return Character.toString(selector);
        }
        return null;
    }
    public static String parseEscapeSequence(String input,
                                             String allowedSpecial,
                                             String allowedRaw) {
        String ret = tryParseEscapeSequence(input, allowedSpecial,
                                            allowedRaw);
        if (ret == null)
            throw new IllegalArgumentException("Invalid escape sequence " +
                input);
        return ret;
    }

    private static void formatStringPart(int mode, String str, int copyFrom,
                                         int copyTo, StringBuilder out) {
        if (out.length() > 0) out.append('+');
        switch (mode) {
            case 1: case 2:
                out.append('"').append(str, copyFrom, copyTo).append('"');
                break;
            case 3:
                out.append('\'').append(str, copyFrom, copyTo).append('\'');
                break;
            case 4:
                boolean seenNonControl = false;
                for (int ch, i = copyFrom;
                     i < copyTo;
                     i += Character.charCount(ch)) {
                    ch = Character.codePointAt(str, i);
                    if (ch <= ' ' || ch >= '\177' && ch <= '\237') continue;
                    if (! seenNonControl) out.append('"');
                    seenNonControl = true;
                    out.appendCodePoint(ch);
                }
                out.append((seenNonControl) ? "\"(U+" : "<U+");
                boolean first = true;
                for (int i = copyFrom, ch;
                     i < copyTo;
                     i += Character.charCount(ch)) {
                    ch = Character.codePointAt(str, i);
                    if (first) {
                        first = false;
                    } else {
                        out.append(',');
                    }
                    out.append(String.format("%04X", ch));
                }
                out.append((seenNonControl) ? ')' : '>');
                break;
        }
    }
    public static String formatString(String str) {
        StringBuilder res = new StringBuilder();
        int mode = 0, copyFrom = 0, i = 0;
        for (int ch, l = str.length(); i < l; i += Character.charCount(ch)) {
            ch = Character.codePointAt(str, i);
            int newMode;
            if (ch == '\'') {
                newMode = 2;
            } else if (ch == '"') {
                newMode = 3;
            } else if (ch >= ' ' && ch <= '~') {
                newMode = (mode >= 1 && mode <= 3) ? mode : 1;
            } else {
                newMode = 4;
            }
            if (mode != newMode) {
                if (mode != 1 || (newMode != 2 && newMode != 3)) {
                    formatStringPart(mode, str, copyFrom, i, res);
                    copyFrom = i;
                }
                mode = newMode;
            }
        }
        formatStringPart(mode, str, copyFrom, i, res);
        if (res.length() == 0) res.append("\"\"");
        return res.toString();
    }

    private static String escapeSpecialPattern(char ch) {
        if (ch >= ' ' && ch <= '~') {
            return "\\" + ch;
        } else if (ch < REGEX_ESCAPE.length && REGEX_ESCAPE[ch]) {
            return ESCAPES[ch];
        } else {
            return String.format("\\u%04X", (int) ch);
        }
    }
    @SuppressWarnings("fallthrough")
    public static String formatPattern(Pattern pat) {
        StringBuffer res = new StringBuffer("/");
        // We need to (partially) re-parse the pattern in order to avoid
        // introducing backslashes into \Q-\E groups.
        Matcher m = PATTERN_INTERESTING.matcher(pat.pattern());
        int state = FPS_NORMAL, dp = 0, lastIndex = -1;
        while (m.find()) {
            String replacement = m.group();
            if (state == FPS_ENTER_QUOTE) {
                if (m.start() == lastIndex && replacement.equals("\\E")) {
                    state = FPS_SKIP;
                } else {
                    res.append("\\Q");
                    state = FPS_QUOTE;
                }
            }
            // The actual replacement will be appended directly, avoiding
            // the need to escape backslashes and dollar signs.
            m.appendReplacement(res, "");
            char found = replacement.charAt(0);
            switch (found) {
                case '[':
                    // Character classes must be recognized as they have a
                    // different set of meta-characters (crucially not
                    // including \Q), and can be recursively nested.
                    if (state == FPS_NORMAL) state = FPS_CLASS;
                    if (state == FPS_CLASS) dp++;
                    break;
                case ']':
                    if (state == FPS_CLASS && --dp == 0) state = FPS_NORMAL;
                    break;
                case '\\':
                    found = replacement.charAt(1);
                    if (found == 'Q') {
                        if (state == FPS_NORMAL) {
                            state = FPS_ENTER_QUOTE;
                            replacement = "";
                        }
                        break;
                    } else if (found == 'E') {
                        if (state == FPS_QUOTE) {
                            state = FPS_NORMAL;
                        } else if (state == FPS_SKIP) {
                            state = FPS_NORMAL;
                            replacement = "";
                        }
                        break;
                    }
                default:
                    // In order to allow the resulting string to be recognized
                    // without (as is done by the parser module's
                    // meta-grammar) writing another partial parser for Java's
                    // regular expression language, and to avoid control
                    // characters in the output, we escape control characters
                    // (and slashes) by exiting a \Q-\E group if necessary and
                    // substituting an escape sequence with a backslash.
                    replacement = escapeSpecialPattern(found);
                    if (state == FPS_QUOTE) {
                        res.append("\\E");
                        state = FPS_ENTER_QUOTE;
                    }
                    break;
            }
            res.append(replacement);
            lastIndex = m.end();
        }
        m.appendTail(res);
        res.append('/');
        int flags = pat.flags();
        for (Map.Entry<Integer, Character> ent : REGEX_FLAGS.entrySet()) {
            int f = ent.getKey();
            if ((flags & f) == 0) continue;
            flags &= f;
            res.append(ent.getValue());
        }
        if (flags != 0) {
            res.append('0').append(Long.toOctalString(flags & 0xFFFFFFFFL));
        }
        return res.toString();
    }

}
