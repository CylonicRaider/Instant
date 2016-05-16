package net.instant;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
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
        l.add(new Draft_Raw());
        l.addAll(WebSocketImpl.defaultdraftlist);
        DEFAULT_DRAFTS = Collections.unmodifiableList(l);
    }

    public interface Hook {

        boolean allowUnassigned();

        Boolean verifyWebSocket(ClientHandshake request,
                                boolean guess);

        void postProcessRequest(InstantWebSocketServer parent,
                                InformationCollector.Datum info,
                                ClientHandshake request,
                                ServerHandshakeBuilder response,
                                Handshakedata eff_resp);

        void onOpen(InformationCollector.Datum info,
                    WebSocket conn, ClientHandshake handshake);
        void onClose(WebSocket conn, int code, String reason,
                     boolean remote);
        void onMessage(WebSocket conn, String message);
        void onMessage(WebSocket conn, ByteBuffer message);
        void onError(WebSocket conn, Exception ex);

    }

    private final List<Hook> hooks;
    private final Map<InformationCollector.Datum, Hook> queuedAssignments;
    private final Map<WebSocket, Hook> assignments;
    private InformationCollector collector;

    public InstantWebSocketServer(InetSocketAddress addr) {
        super(addr, wrapDrafts(DEFAULT_DRAFTS));
        hooks = Collections.synchronizedList(new ArrayList<Hook>());
        queuedAssignments = Collections.synchronizedMap(
            new HashMap<InformationCollector.Datum, Hook>());
        assignments = Collections.synchronizedMap(
            new HashMap<WebSocket, Hook>());
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
        synchronized (hooks) {
            if (! hooks.contains(h))
                hooks.add(h);
        }
    }
    public boolean removeHook(Hook h) {
        return hooks.remove(h);
    }
    public List<Hook> getHooks() {
        synchronized (hooks) {
            return new ArrayList<Hook>(hooks);
        }
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
        InformationCollector.Datum info = collector.pop(handshake);
        Hook h = clearAssignment(info);
        if (h != null) {
            assign(conn, h);
            h.onOpen(info, conn, handshake);
        } else {
            synchronized (hooks) {
                for (Hook hook : hooks) {
                    if (! hook.allowUnassigned()) break;
                    hook.onOpen(info, conn, handshake);
                }
            }
        }
        System.err.println(info.formatLogEntry());
    }

    public void onClose(WebSocket conn, int code,
                        String reason, boolean remote) {
        Hook h = getAssignment(conn);
        if (h != null) {
            h.onClose(conn, code, reason, remote);
            return;
        }
        synchronized (hooks) {
            for (Hook hook : hooks) {
                if (! hook.allowUnassigned()) break;
                hook.onClose(conn, code, reason, remote);
            }
        }
    }

    public void onMessage(WebSocket conn, String message) {
        Hook h = getAssignment(conn);
        if (h != null) {
            h.onMessage(conn, message);
            return;
        }
        synchronized (hooks) {
            for (Hook hook : hooks) {
                if (! hook.allowUnassigned()) break;
                hook.onMessage(conn, message);
            }
        }
    }
    public void onMessage(WebSocket conn, ByteBuffer message) {
        Hook h = getAssignment(conn);
        if (h != null) {
            h.onMessage(conn, message);
            return;
        }
        synchronized (hooks) {
            for (Hook hook : hooks) {
                if (! hook.allowUnassigned()) break;
                hook.onMessage(conn, message);
            }
        }
    }

    public void onError(WebSocket conn, Exception ex) {
        Hook h = getAssignment(conn);
        if (h != null) {
            h.onError(conn, ex);
            return;
        }
        synchronized (hooks) {
            for (Hook hook : hooks) {
                if (! hook.allowUnassigned()) break;
                hook.onError(conn, ex);
            }
        }
    }

    public void postProcessRequest(ClientHandshake request,
                                   ServerHandshakeBuilder response,
                                   Handshakedata eff_resp) {
        InformationCollector.Datum info = collector.get(request);
        response.put("Server", "Instant/" + Main.VERSION);
        byte[] rand = Util.getRandomness(16);
        response.put("X-Magic-Cookie", '"' + Util.toBase64(rand) + '"');
        Util.clear(rand);
        synchronized (hooks) {
            for (Hook hook : hooks) {
                hook.postProcessRequest(this, info, request,
                                        response, eff_resp);
                if (getAssignment(info) != null) break;
            }
        }
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

    public void assign(InformationCollector.Datum info, Hook hook) {
        queuedAssignments.put(info, hook);
    }
    protected void assign(WebSocket conn, Hook hook) {
        assignments.put(conn, hook);
    }
    public Hook getAssignment(InformationCollector.Datum info) {
        return queuedAssignments.get(info);
    }
    public Hook getAssignment(WebSocket conn) {
        return assignments.get(conn);
    }
    public Hook clearAssignment(InformationCollector.Datum info) {
        return queuedAssignments.remove(info);
    }
    public Hook clearAssignment(WebSocket conn) {
        return assignments.remove(conn);
    }

    public static Draft getEffectiveDraft(InformationCollector.Datum i) {
        Draft d = i.getDraft();
        while (d instanceof DraftWrapper) {
            d = ((DraftWrapper) d).getWrapped();
        }
        return d;
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
