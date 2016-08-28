package net.instant;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.instant.api.API1;
import net.instant.api.Counter;
import net.instant.api.FileGenerator;
import net.instant.api.FileInfo;
import net.instant.api.MessageHook;
import net.instant.api.RequestHook;
import net.instant.hooks.StaticFileHook;
import net.instant.hooks.RoomWebSocketHook;
import net.instant.info.RequestInfo;
import net.instant.proto.MessageInfo;
import net.instant.util.fileprod.FileCell;
import net.instant.util.fileprod.FileProducer;
import net.instant.util.fileprod.Producer;
import net.instant.util.fileprod.ProducerJob;
import net.instant.util.fileprod.StringProducer;
import net.instant.ws.InstantWebSocketServer;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;

public class InstantRunner implements API1, Runnable {

    public static class APIRequestHook
            implements InstantWebSocketServer.Hook {

        private final RequestHook wrapped;

        public APIRequestHook(RequestHook h) {
            wrapped = h;
        }

        public boolean allowUnassigned() {
            return false;
        }

        public Boolean verifyWebSocket(ClientHandshake request,
                                       boolean guess) {
            return null;
        }

        public void postProcessRequest(InstantWebSocketServer parent,
                                       RequestInfo info,
                                       Handshakedata eff_resp) {
            // Implementing multiple interfaces! Wheee!
            if (wrapped.evaluateRequest(info, info)) {
                parent.assign(info, this);
            }
        }

        public void onOpen(RequestInfo info, ClientHandshake handshake) {
            wrapped.onOpen(info);
        }

        public void onMessage(RequestInfo info, String message) {
            wrapped.onInput(info, message);
        }
        public void onMessage(RequestInfo info, ByteBuffer message) {
            wrapped.onInput(info, message);
        }

        public void onClose(RequestInfo info, int code, String reason,
                            boolean remote) {
            wrapped.onClose(info, (code == CloseFrame.NORMAL ||
                                   code == CloseFrame.GOING_AWAY));
        }

        public void onError(RequestInfo info, Exception exc) {
            wrapped.onError(info, exc);
        }

    }

    public static class APIFileCell extends FileCell {

        private final FileInfo wrapped;

        public APIFileCell(FileInfo i) {
            super(i.getName(), i.getData(), i.getCreated());
            wrapped = i;
        }

        public boolean isValid() {
            return wrapped.isValid();
        }

    }

    public static class APIFileProducer implements Producer {

        private final FileGenerator wrapped;

        public APIFileProducer(FileGenerator g) {
            wrapped = g;
        }

        public ProducerJob produce(String path) {
            try {
                if (! wrapped.hasFile(path)) return null;
            } catch (IOException exc) {
                exc.printStackTrace();
                return null;
            }
            final String name = path;
            return new ProducerJob(name) {
                public FileCell produce() throws IOException {
                    return new APIFileCell(wrapped.generateFile(name));
                }
            };
        }

    }

    public static class APIMessageHook implements RoomWebSocketHook.Hook {

        private final List<MessageHook> hooks;

        public APIMessageHook(List<MessageHook> l) {
            hooks = l;
        }

        public boolean processMessage(MessageInfo msg) {
            for (MessageHook h : hooks) {
                if (h.onMessage(msg)) return true;
            }
            return false;
        }

    }

    public static final String SITE_FILE = "/static/site.js";

    private final List<InstantWebSocketServer.Hook> pendingRequestHooks;
    private final List<Producer> pendingProducers;
    private final List<StaticFileHook.AliasMatcher> pendingAliases;
    private final List<MessageHook> messageHooks;
    private final List<String> pendingSiteCode;
    private String host;
    private int port;
    private Counter counter;
    private InstantWebSocketServer server;
    private RoomWebSocketHook roomHook;
    private StaticFileHook fileHook;
    private StringProducer stringProducer;

    public InstantRunner() {
        pendingRequestHooks = new ArrayList<InstantWebSocketServer.Hook>();
        pendingProducers = new ArrayList<Producer>();
        pendingAliases = new ArrayList<StaticFileHook.AliasMatcher>();
        messageHooks = new ArrayList<MessageHook>();
        pendingSiteCode = new ArrayList<String>();
        host = "";
        port = 8080;
        counter = null;
        server = null;
        roomHook = null;
        fileHook = null;
        stringProducer = null;
    }

    public String getHost() {
        return host;
    }
    public void setHost(String h) {
        host = h;
    }

    public int getPort() {
        return port;
    }
    public void setPort(int p) {
        port = p;
    }

    public Counter getCounter() {
        return counter;
    }
    public void setCounter(Counter c) {
        counter = c;
    }

    public InstantWebSocketServer getServer() {
        return server;
    }
    public void setServer(InstantWebSocketServer s) {
        server = s;
    }
    public InstantWebSocketServer makeServer() {
        if (server == null) {
            server = new InstantWebSocketServer(
                new InetSocketAddress(host, port));
            for (InstantWebSocketServer.Hook h : pendingRequestHooks) {
                server.addHook(h);
            }
            pendingRequestHooks.clear();
            if (roomHook != null) server.addHook(roomHook);
            if (fileHook != null) server.addHook(fileHook);
        }
        return server;
    }
    public void addRequestHook(InstantWebSocketServer.Hook h) {
        if (server == null) {
            pendingRequestHooks.add(h);
        } else {
            server.addHook(h);
        }
    }
    public void addRequestHook(RequestHook h) {
        addRequestHook(new APIRequestHook(h));
    }

    public RoomWebSocketHook getRoomHook() {
        return roomHook;
    }
    public void setRoomHook(RoomWebSocketHook h) {
        roomHook = h;
    }
    public RoomWebSocketHook makeRoomHook() {
        if (roomHook == null) {
            roomHook = new RoomWebSocketHook();
            roomHook.setHook(new APIMessageHook(messageHooks));
        }
        return roomHook;
    }
    public void addMessageHook(MessageHook h) {
        messageHooks.add(h);
    }

    public StaticFileHook getFileHook() {
        return fileHook;
    }
    public void setFileHook(StaticFileHook h) {
        fileHook = h;
    }
    public StaticFileHook makeFileHook() {
        if (fileHook == null) {
            FileProducer prod = new FileProducer();
            fileHook = new StaticFileHook(prod);
            for (Producer p : pendingProducers) {
                prod.addProducer(p);
            }
            pendingProducers.clear();
            for (StaticFileHook.AliasMatcher a : pendingAliases) {
                fileHook.addAlias(a);
            }
            pendingAliases.clear();
            if (stringProducer != null) prod.addProducer(stringProducer);
        }
        return fileHook;
    }
    public void addFileGenerator(Producer p) {
        if (fileHook == null) {
            pendingProducers.add(p);
        } else {
            fileHook.getProducer().addProducer(p);
        }
    }
    public void addFileGenerator(FileGenerator f) {
        addFileGenerator(new APIFileProducer(f));
    }

    public void addFileAlias(StaticFileHook.AliasMatcher a) {
        if (fileHook == null) {
            pendingAliases.add(a);
        } else {
            fileHook.addAlias(a);
        }
    }
    public void addFileAlias(String from, String to) {
        addFileAlias(new StaticFileHook.DefaultMatcher(from, to, false));
    }
    public void addFileAlias(Pattern from, String to) {
        addFileAlias(new StaticFileHook.DefaultMatcher(from, to, true));
    }

    public StringProducer getStringProducer() {
        return stringProducer;
    }
    public void setStringProducer(StringProducer p) {
        stringProducer = p;
    }
    public StringProducer makeStringProducer() {
        if (stringProducer == null) {
            stringProducer = new StringProducer();
            StringBuilder siteCode = new StringBuilder();
            for (String s : pendingSiteCode) {
                siteCode.append(s);
            }
            pendingSiteCode.clear();
            stringProducer.addFile(SITE_FILE, siteCode.toString());
        }
        return stringProducer;
    }
    public void addSiteCode(String c) {
        if (stringProducer == null) {
            pendingSiteCode.add(c);
        } else {
            stringProducer.appendFile(SITE_FILE, c);
        }
    }

    public void run() {
        makeStringProducer();
        makeFileHook();
        makeRoomHook();
        makeServer().run();
    }

}
