package net.instant.ws;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.instant.api.ClientConnection;
import net.instant.api.Cookie;
import net.instant.api.RequestType;
import net.instant.api.ResponseBuilder;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.handshake.HandshakeBuilder;
import org.json.JSONObject;

public class InformationCollector {

    public class Datum implements ClientConnection, ResponseBuilder {

        private ClientHandshake request;
        private ServerHandshakeBuilder response;
        private WebSocket ws;
        private String rfc1413Ident;
        private String authIdent;
        private long timestamp;
        private String reqLine;
        private RequestType reqType;
        private long respLength;
        private List<Cookie> cookies;
        private Map<String, Object> extraData;
        private List<Cookie> respCookies;

        public Datum(long timestamp) {
            this.timestamp = timestamp;
            this.extraData = new LinkedHashMap<String, Object>();
            this.respCookies = new ArrayList<Cookie>();
        }
        public Datum() {
            this(System.currentTimeMillis());
        }

        public InetSocketAddress getAddress() {
            return ws.getRemoteSocketAddress();
        }

        public String getRFC1413Identity() {
            return rfc1413Ident;
        }
        public void setRFC1413Identity(String identity) {
            rfc1413Ident = identity;
        }

        public String getAuthIdentity() {
            return authIdent;
        }
        public void setAuthIdentity(String identity) {
            authIdent = identity;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getRequestLine() {
            return reqLine;
        }

        public String getReferrer() {
            return request.getFieldValue("Referer");
        }

        public String getUserAgent() {
            return request.getFieldValue("User-Agent");
        }

        public RequestType getRequestType() {
            return reqType;
        }

        public Map<String, String> getHeaders() {
            return InstantWebSocketServer.headerMap(request);
        }

        public String getHeader(String name) {
            return request.getFieldValue(name);
        }

        public List<Cookie> getCookies() {
            return Collections.unmodifiableList(cookies);
        }

        public Map<String, Object> getExtraData() {
            return extraData;
        }

        public int getStatusCode() {
            return response.getHttpStatus();
        }

        public String getStatusMessage() {
            return response.getHttpStatusMessage();
        }

        public long getResponseLength() {
            return respLength;
        }

        public Map<String, String> getResponseHeaders() {
            return Collections.unmodifiableMap(
                InstantWebSocketServer.headerMap(response));
        }

        public String getResponseHeader(String name) {
            return response.getFieldValue(name);
        }

        public List<Cookie> getResponseCookies() {
            return respCookies;
        }

        public Long getDeadline() {
            return parent.getConnectionGC().getDeadline(this);
        }

        public void setDeadline(Long time) {
            if (time == null) {
                parent.getConnectionGC().removeDeadline(this);
            } else {
                parent.getConnectionGC().setDeadline(this, time);
            }
        }

        public WebSocket getConnection() {
            return ws;
        }

        public void respond(int code, String message, long length) {
            response.setHttpStatus((short) code);
            response.setHttpStatusMessage(message);
            respLength = length;
        }

        public void addHeader(String name, String content) {
            response.put(name, content);
        }

        public Cookie makeCookie(String name, String value) {
            return parent.getCookieHandler().makeCookie(name, value);
        }

        public Cookie makeCookie(String name, JSONObject data) {
            return parent.getCookieHandler().makeCookie(name, data);
        }

        public void addCookie(Cookie cookie) {
            respCookies.add(cookie);
        }

        protected void initRequestLine(String line) {
            reqLine = line;
        }
        protected void initRequest(WebSocket conn, Draft draft,
                                   ClientHandshake handshake) {
            ws = conn;
            request = handshake;
            reqType = InstantWebSocketServer.getRequestType(draft);
            cookies = parent.getCookieHandler().extractCookies(handshake);
        }
        protected void initResponse(ServerHandshakeBuilder handshake) {
            response = handshake;
        }
        protected void postProcess() {
            parent.getCookieHandler().setCookies(response, respCookies);
        }

    }

    private final InstantWebSocketServer parent;
    private final Map<Handshakedata, Datum> requests;
    private final Map<WebSocket, Datum> connections;

    public InformationCollector(InstantWebSocketServer parent) {
        this.parent = parent;
        this.requests = new HashMap<Handshakedata, Datum>();
        this.connections = new HashMap<WebSocket, Datum>();
    }

    public InstantWebSocketServer getParent() {
        return parent;
    }

    public synchronized Datum addRequestLine(Handshakedata handshake,
                                             String line) {
        Datum d = new Datum();
        d.initRequestLine(line);
        requests.put(handshake, d);
        return d;
    }
    public synchronized Datum addRequestData(WebSocket conn, Draft draft,
                                             ClientHandshake request) {
        Datum d = requests.get(request);
        d.initRequest(conn, draft, request);
        connections.put(conn, d);
        return d;
    }
    public synchronized Datum addResponse(ClientHandshake request,
                                          ServerHandshakeBuilder response,
                                          HandshakeBuilder result) {
        Datum d = requests.remove(request);
        d.initResponse((ServerHandshakeBuilder) result);
        return d;
    }
    public void postProcess(Datum d) {
        d.postProcess();
    }

}
