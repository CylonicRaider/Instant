package net.instant.info;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.instant.api.Cookie;
import net.instant.api.ResponseBuilder;
import net.instant.api.RequestResponseData;
import net.instant.api.RequestType;
import net.instant.ws.CookieHandler;
import net.instant.ws.Draft_Raw;
import net.instant.ws.Draft_SSE;
import net.instant.ws.InstantWebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.json.JSONObject;

public class RequestInfo implements RequestResponseData, ResponseBuilder {

    private final Datum base;
    private final ClientHandshake client;
    private final ServerHandshakeBuilder server;
    private final CookieHandler cookies;
    private final List<Cookie> requestCookies;
    private final List<Cookie> responseCookies;
    private RequestType reqType;

    public RequestInfo(Datum base, ClientHandshake client,
                       ServerHandshakeBuilder server,
                       CookieHandler cookies) {
        this.base = base;
        this.client = client;
        this.server = server;
        this.cookies = cookies;
        this.requestCookies = new ArrayList<Cookie>(cookies.get(client));
        this.responseCookies = new ArrayList<Cookie>();
        this.reqType = null;
    }

    public Datum getBase() {
        return base;
    }
    public ClientHandshake getClientData() {
        return client;
    }
    public ServerHandshakeBuilder getServerData() {
        return server;
    }

    public InetSocketAddress getAddress() {
        return base.getSourceAddress();
    }

    public String getRFC1413Identity() {
        return base.getRFC1413();
    }

    public String getAuthIdentity() {
        return base.getAuth();
    }

    public long getTimestamp() {
        return base.getTimestamp();
    }

    public String getRequestLine() {
        return base.getRequestLine();
    }

    public String getReferrer() {
        return base.getReferer(); // sic
    }

    public String getUserAgent() {
        return base.getUserAgent();
    }

    public synchronized RequestType getRequestType() {
        if (reqType != null) return reqType;
        Draft d = InstantWebSocketServer.getEffectiveDraft(this);
        if (d == null)
            throw new IllegalStateException("Request type not decided");
        if (d instanceof Draft_SSE) {
            reqType = RequestType.SSE;
        } else if (d instanceof Draft_Raw) {
            reqType = RequestType.HTTP;
        } else {
            reqType = RequestType.WS;
        }
        return reqType;
    }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headerMap(client));
    }

    public String getHeader(String name) {
        return client.getFieldValue(name);
    }

    public List<Cookie> getCookies() {
        return Collections.unmodifiableList(requestCookies);
    }

    public Map<String, Object> getExtraData() {
        return base.getExtra();
    }

    public int getStatusCode() {
        return base.getCode();
    }

    public String getStatusMessage() {
        return base.getMessage();
    }

    public long getResponseLength() {
        return base.getLength();
    }

    public Map<String, String> getResponseHeaders() {
        return Collections.unmodifiableMap(headerMap(server));
    }

    public String getResponseHeader(String name) {
        return server.getFieldValue(name);
    }

    public List<Cookie> getResponseCookies() {
        return Collections.unmodifiableList(responseCookies);
    }

    public WebSocket getConnection() {
        return base.getWebSocket();
    }

    public void setRFC1413Identity(String identity) {
        base.setRFC1413(identity);
    }

    public void setAuthIdentity(String identity) {
        base.setAuth(identity);
    }

    public void respond(int code, String message, long length) {
        base.setResponseInfo(server, (short) code, message, length);
    }

    public void putHeader(String name, String content) {
        server.put(name, content);
    }

    public Cookie makeCookie(String name, String value) {
        return cookies.make(name, value);
    }

    public Cookie makeCookie(String name, JSONObject data) {
        return cookies.make(name, data);
    }

    public void putCookie(Cookie cookie) {
        responseCookies.add(cookie);
    }

    public static Map<String, String> headerMap(Handshakedata d) {
        Map<String, String> ret = new LinkedHashMap<String, String>();
        Iterator<String> names = d.iterateHttpFields();
        while (names.hasNext()) {
            String name = names.next();
            ret.put(name, d.getFieldValue(name));
        }
        return ret;
    }

}
