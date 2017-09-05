package net.instant.ws;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.instant.api.RequestHook;
import net.instant.api.RequestType;
import net.instant.util.StringSigner;
import net.instant.util.Util;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;

public class InstantWebSocketServer extends WebSocketServer
        implements Draft_Raw.ConnectionVerifier, DraftWrapper.Hook {

    public static final List<Draft> DEFAULT_DRAFTS;

    public static final boolean INSECURE_COOKIES;
    public static final File COOKIES_KEYFILE;

    static {
        List<Draft> l =  new ArrayList<Draft>();
        l.add(new Draft_SSE());
        l.add(new Draft_Raw());
        l.addAll(WebSocketImpl.defaultdraftlist);
        DEFAULT_DRAFTS = Collections.unmodifiableList(l);
        INSECURE_COOKIES = Util.isTrue(
            Util.getConfiguration("instant.cookies.insecure"));
        COOKIES_KEYFILE = new File(
            Util.getConfiguration("instant.cookies.keyfile"));
    }

    private final Set<RequestHook> hooks;
    private final Set<RequestHook> internalHooks;
    private final Map<WebSocket, RequestHook> assignments;
    private InformationCollector collector;
    private CookieHandler cookies;
    private ConnectionGC gc;

    public InstantWebSocketServer(InetSocketAddress addr) {
        super(addr, wrapDrafts(DEFAULT_DRAFTS));
        hooks = new LinkedHashSet<RequestHook>();
        internalHooks = new LinkedHashSet<RequestHook>();
        assignments = Collections.synchronizedMap(
            new HashMap<WebSocket, RequestHook>());
        collector = new InformationCollector(this);
        gc = new ConnectionGC();
        cookies = new CookieHandler(makeStringSigner());
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
        InformationCollector.Datum d = collector.addResponse(
            request, response, result);
        for (RequestHook h : getAllHooks()) {
            if (h.evaluateRequest(d, d)) {
                assignments.put(d.getConnection(), h);
                break;
            }
        }
        collector.postProcess(d);
    }

    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        RequestHook h = assignments.get(conn);
        InformationCollector.Datum d = collector.get(conn);
        if (h != null) h.onOpen(d);
    }

    public void onMessage(WebSocket conn, String message) {
        RequestHook h = assignments.get(conn);
        InformationCollector.Datum d = collector.get(conn);
        if (h != null) h.onInput(d, message);
    }

    public void onMessage(WebSocket conn, ByteBuffer message) {
        RequestHook h = assignments.get(conn);
        InformationCollector.Datum d = collector.get(conn);
        if (h != null) h.onInput(d, message);
    }

    public void onClose(WebSocket conn, int code, String reason,
                        boolean remote) {
        RequestHook h = assignments.get(conn);
        InformationCollector.Datum d = collector.get(conn);
        if (h != null)
            h.onClose(d, (code == CloseFrame.NORMAL ||
                          code == CloseFrame.GOING_AWAY));
    }

    public void onError(WebSocket conn, Exception ex) {
        if (conn == null) {
            for (RequestHook h : getAllHooks()) h.onError(null, ex);
            return;
        }
        RequestHook h = assignments.get(conn);
        InformationCollector.Datum d = collector.get(conn);
        if (h != null) h.onError(d, ex);
    }

    public Iterable<RequestHook> getAllHooks() {
        return new Iterable<RequestHook>() {
            public Iterator<RequestHook> iterator() {
                return new Iterator<RequestHook>() {

                    private boolean atInternal = false;
                    private Iterator<RequestHook> wrapped = hooks.iterator();

                    public boolean hasNext() {
                        boolean hn = wrapped.hasNext();
                        if (! hn && ! atInternal) {
                            atInternal = true;
                            wrapped = internalHooks.iterator();
                            hn = wrapped.hasNext();
                        }
                        return hn;
                    }

                    public RequestHook next() {
                        // Swap iterator out if necessary.
                        hasNext();
                        return wrapped.next();
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                };
            }
        };
    }
    public Set<RequestHook> getHooks() {
        return Collections.unmodifiableSet(hooks);
    }
    public Set<RequestHook> getInternalHooks() {
        return Collections.unmodifiableSet(internalHooks);
    }
    public void addHook(RequestHook hook) {
        hooks.add(hook);
    }
    public void addInternalHook(RequestHook hook) {
        internalHooks.add(hook);
    }
    public void removeHook(RequestHook hook) {
        hooks.remove(hook);
        internalHooks.remove(hook);
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

    public static StringSigner makeStringSigner() {
        if (INSECURE_COOKIES) {
            return null;
        } else if (COOKIES_KEYFILE != null) {
            return StringSigner.getInstance(COOKIES_KEYFILE);
        } else {
            return StringSigner.getInstance(Util.getRandomness(64));
        }
    }

}
