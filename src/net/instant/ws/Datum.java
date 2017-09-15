package net.instant.ws;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
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
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.json.JSONObject;

public class Datum implements ClientConnection, ResponseBuilder {

    private final InstantWebSocketServer parent;
    private ClientHandshake request;
    private ServerHandshakeBuilder response;
    private WebSocket ws;
    private String rfc1413Ident;
    private String authIdent;
    private long timestamp;
    private String reqMethod;
    private String reqPath;
    private String reqVersion;
    private RequestType reqType;
    private long respLength;
    private List<Cookie> cookies;
    private Map<String, Object> extraData;
    private List<Cookie> respCookies;

    public Datum(InstantWebSocketServer parent, long timestamp) {
        this.parent = parent;
        this.timestamp = timestamp;
        this.extraData = new LinkedHashMap<String, Object>();
        this.respCookies = new ArrayList<Cookie>();
    }
    public Datum(InstantWebSocketServer parent) {
        this(parent, System.currentTimeMillis());
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

    public String getMethod() {
        return reqMethod;
    }

    public String getPath() {
        return reqPath;
    }

    public String getHTTPVersion() {
        return reqVersion;
    }

    public String getReferrer() {
        return getHeader("Referer");
    }

    public String getUserAgent() {
        return getHeader("User-Agent");
    }

    public RequestType getRequestType() {
        return reqType;
    }

    public Map<String, String> getHeaders() {
        return InstantWebSocketServer.headerMap(request);
    }

    public String getHeader(String name) {
        String ret = request.getFieldValue(name);
        return (ret.isEmpty()) ? null : ret;
    }

    public List<Cookie> getCookies() {
        return Collections.unmodifiableList(cookies);
    }

    public Cookie getCookie(String name) {
        for (Cookie c : cookies) {
            if (c.getName().equals(name)) return c;
        }
        return null;
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
        String ret = response.getFieldValue(name);
        return (ret.isEmpty()) ? null : ret;
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
        if (length != -1)
            response.put("Content-Length", String.valueOf(length));
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
        String[] parts = line.split("\\s+");
        assert (parts.length == 3) : "Bad HTTP request line?!";
        reqMethod = parts[0];
        reqPath = parts[1];
        reqVersion = parts[2];
    }
    protected void initRequest(WebSocket conn, Draft draft,
                               ClientHandshake handshake) {
        ws = conn;
        request = handshake;
        reqType = DraftWrapper.getRequestType(draft);
        cookies = parent.getCookieHandler().extractCookies(handshake);
        String fwd = handshake.getFieldValue("X-Forwarded-For");
        if (! fwd.isEmpty())
            extraData.put("real-ip", fwd.replace(" ", ""));
    }
    protected void initResponse(ServerHandshakeBuilder handshake) {
        response = handshake;
    }
    protected void postProcess() {
        parent.getCookieHandler().setCookies(response, respCookies);
    }

}
