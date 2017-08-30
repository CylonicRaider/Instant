package net.instant.ws;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;

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

    public InstantWebSocketServer(InetSocketAddress addr) {
        super(addr, wrapDrafts(DEFAULT_DRAFTS));
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

    public boolean verifyConnection(ClientHandshake handshakedata,
                                    boolean guess) {
        return guess;
    }

    public void postProcess(ClientHandshake request,
                            ServerHandshakeBuilder response,
                            HandshakeBuilder result) {
        /* NYI */
    }

    public void handleRequestLine(Handshakedata handshake, String line) {
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

}
