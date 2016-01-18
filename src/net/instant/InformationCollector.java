package net.instant;

import java.net.InetAddress;
import java.util.Calendar;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;

public class InformationCollector {

    public interface Hook {

        void postProcessRequest(ClientHandshake request,
                                ServerHandshakeBuilder response,
                                Handshakedata eff_resp);

    }

    public static class Datum {

        private long timestamp;
        private WebSocket webSocket;
        private Draft draft;
        private InetAddress sourceIP;
        private String statusLine;
        private String method, url, version;
        private String referer, userAgent;
        private String rfc1413, auth;
        private short code = -1;
        private long length = -1;

        public Datum() {
            timestamp = System.currentTimeMillis();
        }

        protected void setWebSocket(WebSocket s) {
            webSocket = s;
        }
        protected void setDraft(Draft d) {
            draft = d;
        }
        protected void setSourceIP(InetAddress a) {
            sourceIP = a;
        }
        protected void setStatusLine(String line) {
            String[] tokens = line.split(" ");
            if (tokens.length != 3)
                throw new IllegalArgumentException("Invalid status line");
            statusLine = line;
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
        protected void setAuthInfo(String r, String a) {
            rfc1413 = r;
            auth = a;
        }

        public void setResponseInfo(short c, long l) {
            code = c;
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
        public InetAddress getSourceIP() {
            return sourceIP;
        }

        public String getStatusLine() {
            return statusLine;
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

        public short getCode() {
            return code;
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
            return String.format("%s %s %s %s %s %s %s %s %s",
                formatElement(sourceIP), formatElement(rfc1413),
                formatElement(auth), formatElementDatetime(timestamp),
                formatElementQuoted(statusLine), formatElement(code),
                formatElement(length), formatElementQuoted(referer),
                formatElementQuoted(userAgent));
        }

        public void setResponseInfo(ServerHandshakeBuilder response,
                                    short code, String message, long size) {
            setResponseInfo(code, size);
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

    private Map<Handshakedata, Datum> mapping =
        new IdentityHashMap<Handshakedata, Datum>();
    private Hook hook;

    public InformationCollector(Hook hook) {
        super();
        this.hook = hook;
    }
    public InformationCollector() {
        this(null);
    }

    public Datum get(Handshakedata data) {
        Datum entry = mapping.get(data);
        if (entry == null) {
            entry = new Datum();
            mapping.put(data, entry);
        }
        return entry;
    }
    public Datum pop(Handshakedata data) {
        return mapping.remove(data);
    }

    public Hook getHook() {
        return this.hook;
    }
    public void setHook(Hook hook) {
        this.hook = hook;
    }

    public void addWebSocket(ClientHandshake request, WebSocket sock,
                             Draft draft) {
        get(request).setWebSocket(sock);
        get(request).setDraft(draft);
        get(request).setSourceIP(sock.getRemoteSocketAddress().getAddress());
    }

    public void addStatusLine(Handshakedata data, String line) {
        get(data).setStatusLine(line);
    }

    public void postProcess(ClientHandshake request,
                            ServerHandshakeBuilder response,
                            Handshakedata eff_resp) {
        get(request).setReferer(getValue(request, "Referer"));
        get(request).setUserAgent(getValue(request, "User-Agent"));
        if (hook != null) hook.postProcessRequest(request, response, eff_resp);
    }

    public void reset() {
        mapping.clear();
    }

    private String getValue(ClientHandshake request, String k) {
        String v = request.getFieldValue(k);
        return (v == null || v.isEmpty()) ? null : v;
    }

}
