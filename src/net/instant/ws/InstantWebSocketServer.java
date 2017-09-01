package net.instant.ws;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import net.instant.api.RequestType;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.exceptions.InvalidDataException;
import java.util.Iterator;

public class InstantWebSocketServer extends WebSocketServer
        implements Draft_Raw.ConnectionVerifier, DraftWrapper.Hook {

    public static final List<Draft> DEFAULT_DRAFTS;

    static {
        List<Draft> l =  new ArrayList<Draft>();
        l.add(new Draft_SSE());
        l.add(new Draft_Raw());
        l.addAll(WebSocketImpl.defaultdraftlist);
        DEFAULT_DRAFTS = Collections.unmodifiableList(l);
    }

    private InformationCollector collector;
    private CookieHandler cookies;
    private ConnectionGC gc;

    public InstantWebSocketServer(InetSocketAddress addr) {
        super(addr, wrapDrafts(DEFAULT_DRAFTS));
        collector = new InformationCollector(this);
        gc = new ConnectionGC();
        // TODO: Get a StringSigner instance here.
        cookies = new CookieHandler();
        for (Draft d : getDraft()) {
            if (d instanceof DraftWrapper) {
                ((DraftWrapper) d).setHook(this);
                Draft w = ((DraftWrapper) d).getWrapped();
                if (w instanceof Draft_Raw) {
                    ((Draft_Raw) w).setVerifier(this);
                }
            }
        }
    }
    public InstantWebSocketServer(int port) {
        this(new InetSocketAddress(port));
    }

    public CookieHandler getCookieHandler() {
        return cookies;
    }
    public void setCookieHandler(CookieHandler c) {
        cookies = c;
    }

    public ConnectionGC getConnectionGC() {
        return gc;
    }
    public void setConnectionGC(ConnectionGC g) {
        gc = g;
    }

    public boolean verifyConnection(ClientHandshake handshakedata,
                                    boolean guess) {
        return guess;
    }

    /* Calling order:
     * 1. handleRequestLine (via DraftWrapper from translateHandshake)
     * 2. onWebsocketHandshakeReceivedAsServer (directly)
     * 3. postProcess (via DraftWrapper from
     *    postProcessHandshakeResponseAsServer) */
    public void handleRequestLine(Handshakedata handshake, String line) {
        collector.addRequestLine(handshake, line);
    }

    /* That method name is impressive. */
    @Override
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(
            WebSocket conn, Draft draft, ClientHandshake request)
            throws InvalidDataException {
        collector.addRequestData(conn, draft, request);
        return super.onWebsocketHandshakeReceivedAsServer(
            conn, draft, request);
    }

    public void postProcess(ClientHandshake request,
                            ServerHandshakeBuilder response,
                            HandshakeBuilder result) {
        /* NYI */
    }

    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        /* NYI */
    }

    public void onMessage(WebSocket conn, String message) {
        /* NYI */
    }

    public void onMessage(WebSocket conn, ByteBuffer message) {
        /* NYI */
    }

    public void onClose(WebSocket conn, int code, String reason,
                        boolean remote) {
        /* NYI */
    }

    public void onError(WebSocket conn, Exception ex) {
        /* NYI */
    }

    protected static List<Draft> wrapDrafts(List<Draft> in) {
        List<Draft> out = new ArrayList<Draft>(in.size());
        for (Draft d : in) {
            if (d instanceof DraftWrapper) {
                out.add(d.copyInstance());
            } else {
                out.add(new DraftWrapper(d));
            }
        }
        return out;
    }

    protected static RequestType getRequestType(Draft draft) {
        if (draft instanceof DraftWrapper) {
            return getRequestType(((DraftWrapper) draft).getWrapped());
        } else if (draft instanceof Draft_SSE) {
            return RequestType.SSE;
        } else if (draft instanceof Draft_Raw) {
            return RequestType.HTTP;
        } else {
            return RequestType.WS;
        }
    }

    protected static Map<String, String> headerMap(Handshakedata dat) {
        Map<String, String> ret = new LinkedHashMap<String, String>();
        Iterator<String> names = dat.iterateHttpFields();
        while (names.hasNext()) {
            String name = names.next();
            ret.put(name, dat.getFieldValue(name));
        }
        return ret;
    }

}
