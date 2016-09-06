package net.instant.ws;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.instant.Main;
import net.instant.hooks.CookieHandler;
import net.instant.info.Datum;
import net.instant.info.InformationCollector;
import net.instant.info.RequestInfo;
import net.instant.util.Util;
import net.instant.ws.Draft_Raw;
import net.instant.ws.Draft_SSE;
import net.instant.ws.DraftWrapper;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketAdapter;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.DefaultWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;

public class InstantWebSocketServer extends WebSocketServer
        implements InformationCollector.Hook, Draft_Raw.ConnectionVerifier {

    public class InstantWebSocketServerFactory
            extends DefaultWebSocketServerFactory {

        @Override
        public WebSocketImpl createWebSocket(WebSocketAdapter a, Draft d,
                                             Socket s) {
            try {
                s.setKeepAlive(true);
            } catch (SocketException exc) {
                exc.printStackTrace();
            }
            return super.createWebSocket(a, d, s);
        }
        @Override
        public WebSocketImpl createWebSocket(WebSocketAdapter a,
                                             List<Draft> d, Socket s) {
            try {
                s.setKeepAlive(true);
            } catch (SocketException exc) {
                exc.printStackTrace();
            }
            return super.createWebSocket(a, d, s);
        }

    }

    public static final List<Draft> DEFAULT_DRAFTS;

    static {
        List<Draft> l =  new ArrayList<Draft>();
        l.add(new Draft_SSE());
        l.add(new Draft_Raw());
        l.addAll(WebSocketImpl.defaultdraftlist);
        DEFAULT_DRAFTS = Collections.unmodifiableList(l);
    }

    public interface Hook {

        boolean allowUnassigned();

        Boolean verifyWebSocket(ClientHandshake request,
                                boolean guess);

        void postProcessRequest(InstantWebSocketServer parent,
                                RequestInfo info,
                                Handshakedata eff_resp);

        void onOpen(RequestInfo info, ClientHandshake handshake);
        void onClose(RequestInfo info, int code, String reason,
                     boolean remote);
        void onMessage(RequestInfo info, String message);
        void onMessage(RequestInfo info, ByteBuffer message);
        void onError(RequestInfo info, Exception ex);

    }

    public static final boolean INSECURE_COOKIES;

    static {
        String cfg = Util.getConfiguration("instant.cookies.insecure");
        INSECURE_COOKIES = (cfg != null);
    }

    private final List<Hook> hooks;
    private final List<Hook> internalHooks;
    private final Map<Datum, RequestInfo> infos;
    private final Map<RequestInfo, Hook> assignments;
    private InformationCollector collector;
    private CookieHandler cookies;

    public InstantWebSocketServer(InetSocketAddress addr) {
        super(addr, wrapDrafts(DEFAULT_DRAFTS));
        hooks = new ArrayList<Hook>();
        internalHooks = new ArrayList<Hook>();
        infos = new HashMap<Datum, RequestInfo>();
        assignments = new HashMap<RequestInfo, Hook>();
        collector = new InformationCollector(this);
        for (Draft d : getDraft()) {
            if (d instanceof DraftWrapper) {
                ((DraftWrapper) d).setCollector(collector);
                Draft w = ((DraftWrapper) d).getWrapped();
                if (w instanceof Draft_Raw) {
                    ((Draft_Raw) w).setVerifier(this);
                }
            }
        }
        setWebSocketFactory(new InstantWebSocketServerFactory());
    }
    public InstantWebSocketServer(int port) {
        this(new InetSocketAddress(port));
    }

    public void addHook(Hook h) {
        hooks.add(h);
    }
    public boolean removeHook(Hook h) {
        return hooks.remove(h);
    }
    public List<Hook> getHooks() {
        return Collections.unmodifiableList(hooks);
    }

    public void addInternalHook(Hook h) {
        internalHooks.add(h);
    }
    public boolean removeInternalHook(Hook h) {
        return internalHooks.remove(h);
    }
    public List<Hook> getInternalHooks() {
        return Collections.unmodifiableList(internalHooks);
    }

    public Iterable<Hook> getAllHooks() {
        return new Iterable<Hook>() {
            public Iterator<Hook> iterator() {
                return new Iterator<Hook>() {

                    private Iterator<Hook> it = hooks.iterator();
                    private boolean internal = false;

                    public boolean hasNext() {
                        if (it.hasNext()) return true;
                        if (internal) return false;
                        it = internalHooks.iterator();
                        internal = true;
                        return it.hasNext();
                    }

                    public Hook next() {
                        return it.next();
                    }

                    public void remove() {
                        it.remove();
                    }

                };
            }
        };
    }

    public CookieHandler getCookieHandler() {
        return cookies;
    }
    public void setCookieHandler(CookieHandler h) {
        cookies = h;
    }

    /* Who on the world would give methods such long names? */
    @Override
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(
            WebSocket conn, Draft draft, ClientHandshake request)
            throws InvalidDataException {
        collector.addWebSocket(request, conn, draft);
        return super.onWebsocketHandshakeReceivedAsServer(conn,
                                                          draft,
                                                          request);
    }

    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        RequestInfo info = getInfo(collector.move(handshake, conn));
        Hook h = getAssignment(info);
        if (h != null) {
            assign(info, h);
            h.onOpen(info, handshake);
        } else {
            for (Hook hook : getAllHooks()) {
                if (! hook.allowUnassigned()) break;
                hook.onOpen(info, handshake);
            }
        }
        System.err.println(info.getBase().formatLogEntry());
    }

    public void onClose(WebSocket conn, int code,
                        String reason, boolean remote) {
        RequestInfo info = popInfo(conn);
        Hook h = clearAssignment(info);
        if (h != null) {
            h.onClose(info, code, reason, remote);
            return;
        }
        for (Hook hook : getAllHooks()) {
            if (! hook.allowUnassigned()) break;
            hook.onClose(info, code, reason, remote);
        }
    }

    public void onMessage(WebSocket conn, String message) {
        RequestInfo info = getInfo(conn);
        Hook h = getAssignment(info);
        if (h != null) {
            h.onMessage(info, message);
            return;
        }
        for (Hook hook : getAllHooks()) {
            if (! hook.allowUnassigned()) break;
            hook.onMessage(info, message);
        }
    }
    public void onMessage(WebSocket conn, ByteBuffer message) {
        RequestInfo info = getInfo(conn);
        Hook h = getAssignment(info);
        if (h != null) {
            h.onMessage(info, message);
            return;
        }
        for (Hook hook : getAllHooks()) {
            if (! hook.allowUnassigned()) break;
            hook.onMessage(info, message);
        }
    }

    public void onError(WebSocket conn, Exception ex) {
        RequestInfo info;
        if (conn == null) {
            info = null;
        } else {
            info = getInfo(conn);
            Hook h = getAssignment(info);
            if (h != null) {
                h.onError(info, ex);
                return;
            }
        }
        for (Hook hook : getAllHooks()) {
            if (! hook.allowUnassigned()) break;
            hook.onError(info, ex);
        }
    }

    public void postProcessRequest(ClientHandshake request,
                                   ServerHandshakeBuilder response,
                                   Handshakedata eff_resp) {
        assert eff_resp == response;
        RequestInfo info = makeInfo(collector.get(request), request,
                                    response);
        response.put("Server", "Instant/" + Main.VERSION);
        byte[] rand = Util.getRandomness(16);
        response.put("X-Magic-Cookie", '"' + Util.toBase64(rand) + '"');
        Util.clear(rand);
        response.put("X-Instant-Version", Main.VERSION);
        if (Main.FINE_VERSION != null)
            response.put("X-Instant-Revision", Main.FINE_VERSION);
        for (Hook hook : getAllHooks()) {
            hook.postProcessRequest(this, info, eff_resp);
            if (getAssignment(info) != null) break;
        }
        cookies.set(response, info.getResponseCookies());
    }

    public boolean verifyConnection(ClientHandshake handshakedata,
                                    boolean guess) {
        boolean ret = guess;
        synchronized (hooks) {
            for (Hook hook : hooks) {
                Boolean b = hook.verifyWebSocket(handshakedata,
                                                 guess);
                if (b != null) {
                    ret = b;
                    break;
                }
            }
        }
        return ret;
    }

    public void assign(RequestInfo conn, Hook hook) {
        assignments.put(conn, hook);
    }
    public Hook getAssignment(RequestInfo conn) {
        return assignments.get(conn);
    }
    public Hook clearAssignment(RequestInfo conn) {
        return assignments.remove(conn);
    }

    protected RequestInfo makeInfo(Datum datum, ClientHandshake client,
                                   ServerHandshakeBuilder server) {
        RequestInfo ret = new RequestInfo(datum, client, server,
                                          cookies);
        infos.put(datum, ret);
        return ret;
    }
    protected RequestInfo getInfo(Datum datum) {
        return infos.get(datum);
    }
    protected RequestInfo popInfo(Datum datum) {
        return infos.remove(datum);
    }
    protected RequestInfo getInfo(WebSocket conn) {
        return getInfo(collector.get(conn));
    }
    protected RequestInfo popInfo(WebSocket conn) {
        return getInfo(collector.get(conn));
    }

    public static Draft getEffectiveDraft(Draft d) {
        while (d instanceof DraftWrapper) {
            d = ((DraftWrapper) d).getWrapped();
        }
        return d;
    }
    public static Draft getEffectiveDraft(RequestInfo info) {
        return getEffectiveDraft(info.getBase().getDraft());
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
