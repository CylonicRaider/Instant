package net.instant.info;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;

public class Datum {

    public static class ExtraData extends LinkedHashMap<String, Object> {

        public String formatLogField() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> e : entrySet()) {
                if (e.getValue() == null) continue;
                if (sb.length() != 0) sb.append(' ');
                sb.append(e.getKey());
                sb.append('=');
                sb.append(e.getValue());
            }
            return sb.toString();
        }

    }

    private long timestamp;
    private WebSocket webSocket;
    private Draft draft;
    private InetSocketAddress sourceAddress;
    private String requestLine;
    private String method, url, version;
    private String referer, userAgent;
    private String rfc1413, auth;
    private final ExtraData extra;
    private short code = -1;
    private String message;
    private long length = -1;

    public Datum() {
        timestamp = System.currentTimeMillis();
        extra = new ExtraData();
    }

    protected void setWebSocket(WebSocket s) {
        webSocket = s;
    }
    protected void setDraft(Draft d) {
        draft = d;
    }
    protected void setSourceAddress(InetSocketAddress a) {
        sourceAddress = a;
    }
    protected void setRequestLine(String line) {
        String[] tokens = line.split(" ");
        if (tokens.length != 3)
            throw new IllegalArgumentException("Invalid status line");
        requestLine = line;
        method = tokens[0];
        url = tokens[1];
        version = tokens[2];
    }
    protected void setReferer(String v) {
        referer = v;
    }
    protected void setUserAgent(String v) {
        userAgent = v;
    }
    protected void setRFC1413(String r) {
        rfc1413 = r;
    }
    protected void setAuth(String a) {
        auth = a;
    }
    protected void setAuthInfo(String r, String a) {
        rfc1413 = r;
        auth = a;
    }

    public void setResponseInfo(short c, long l) {
        code = c;
        length = l;
    }
    public void setResponseInfo(short c, String m, long l) {
        code = c;
        message = m;
        length = l;
    }

    public long getTimestamp() {
        return timestamp;
    }
    public WebSocket getWebSocket() {
        return webSocket;
    }
    public Draft getDraft() {
        return draft;
    }
    public InetSocketAddress getSourceAddress() {
        return sourceAddress;
    }

    public String getRequestLine() {
        return requestLine;
    }
    public String getMethod() {
        return method;
    }
    public String getURL() {
        return url;
    }
    public String getVersion() {
        return version;
    }

    public String getRFC1413() {
        return rfc1413;
    }
    public String getAuth() {
        return auth;
    }

    public ExtraData getExtra() {
        return extra;
    }

    public short getCode() {
        return code;
    }
    public String getMessage() {
        return message;
    }
    public long getLength() {
        return length;
    }

    public String getReferer() {
        return referer;
    }
    public String getUserAgent() {
        return userAgent;
    }

    public String formatLogEntry() {
        InetAddress sourceIP;
        if (sourceAddress == null) {
            try {
                sourceIP = InetAddress.getByName("0.0.0.0");
            } catch (UnknownHostException exc) {
                // Should not happen.
                throw new RuntimeException(exc);
            }
        } else {
            sourceIP = sourceAddress.getAddress();
        }
        String ret = String.format("%s %s %s %s %s %s %s %s %s",
            formatElement(sourceIP), formatElement(rfc1413),
            formatElement(auth), formatElementDatetime(timestamp),
            formatElementQuoted(requestLine), formatElement(code),
            formatElement(length), formatElementQuoted(referer),
            formatElementQuoted(userAgent));
        String ef = extra.formatLogField();
        if (! ef.isEmpty()) ret += " " + escapeQuotes(ef, true);
        return ret;
    }

    public void setResponseInfo(ServerHandshakeBuilder response,
                                short code, String message, long size) {
        setResponseInfo(code, message, size);
        response.setHttpStatus(code);
        response.setHttpStatusMessage(message);
    }

    public static String formatElement(String s) {
        return (s == null) ? "-" : escapeQuotes(s, false);
    }
    public static String formatElementQuoted(String s) {
        return (s == null) ? "-" : escapeQuotes(s, true);
    }
    public static String formatElement(InetAddress a) {
        if (a == null) return "-";
        return escapeQuotes(a.getHostAddress(), false);
    }
    public static String formatElement(int i) {
        return (i == -1) ? "-" : Integer.toString(i);
    }
    public static String formatElement(long i) {
        return (i == -1) ? "-" : Long.toString(i);
    }
    public static String formatElementDatetime(long ts) {
        // If you wonder about the "<"-s, RTFM!
        if (ts == -1) return "[-]";
        return String.format((Locale) null, "[%td/%<tb/%<tY:%<tT %<tz]",
                             ts);
    }

    public static String escapeQuotes(String s, boolean quote) {
        if (quote) {
            return '"' + s.replaceAll("\"", "%22") + '"';
        } else {
            return s.replaceAll(" ", "%20").replaceAll("\"", "%22");
        }
    }

}
