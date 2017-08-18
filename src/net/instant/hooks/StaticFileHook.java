package net.instant.hooks;

import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.instant.api.RequestType;
import net.instant.info.RequestInfo;
import net.instant.util.DefaultStringMatcher;
import net.instant.util.StringMatcher;
import net.instant.util.fileprod.FileCell;
import net.instant.util.fileprod.FileProducer;
import net.instant.util.fileprod.ProducerJob;
import net.instant.ws.InstantWebSocketServer;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;

public class StaticFileHook extends HookAdapter {

    public static final int MAX_CACHE_AGE = 3600;

    private static class Callback implements ProducerJob.Callback {

        private final RequestInfo info;

        public Callback(RequestInfo info) {
            this.info = info;
        }

        public void fileProduced(String name, FileCell cell) {
            write(info, cell);
        }

    }

    private final List<StringMatcher> matchers;
    private final List<StringMatcher> aliases;
    private final Map<RequestInfo, String> running;
    private FileProducer producer;

    public StaticFileHook(FileProducer p) {
        producer = p;
        matchers = new ArrayList<StringMatcher>();
        aliases = new ArrayList<StringMatcher>();
        running = new HashMap<RequestInfo, String>();
    }
    public StaticFileHook() {
        this(null);
    }

    public FileProducer getProducer() {
        return producer;
    }
    public void setProducer(FileProducer p) {
        producer = p;
    }

    public void matchContentType(StringMatcher m) {
        matchers.add(m);
    }
    public void matchContentType(Pattern p, String t) {
        matchContentType(new DefaultStringMatcher(p, t, false));
    }
    public void matchContentType(String p, String t) {
        matchContentType(new DefaultStringMatcher(Pattern.compile(p), t,
                                                  false));
    }

    public void alias(StringMatcher a) {
        aliases.add(a);
    }
    public void alias(Pattern p, String t, boolean d) {
        alias(new DefaultStringMatcher(p, t, d));
    }
    public void alias(Pattern p, String t) {
        alias(new DefaultStringMatcher(p, t));
    }
    public void alias(String p, String t) {
        alias(new DefaultStringMatcher(p, t));
    }

    public boolean allowUnassigned() {
        return false;
    }

    public void postProcessRequest(InstantWebSocketServer parent,
                                   RequestInfo info,
                                   Handshakedata eff_resp) {
        if (getProducer() == null) return;
        if (info.getRequestType() != RequestType.HTTP) return;
        if (! "GET".equals(info.getBase().getMethod())) return;
        String path = info.getBase().getURL().replaceFirst("\\?.*$", "");
        processAs(parent, info, path);
    }

    public boolean processAs(InstantWebSocketServer parent,
                             RequestInfo info, String urls) {
        for (StringMatcher m : aliases) {
            String r = m.match(urls);
            if (r != null) {
                urls = r;
                break;
            }
        }
        return processAsEx(parent, info, urls);
    }

    public boolean processAsEx(InstantWebSocketServer parent,
                               RequestInfo info, String url) {
        FileCell cell;
        long size = -1;
        try {
            cell = getProducer().get(url);
            if (cell != null) size = cell.getSize();
        } catch (FileNotFoundException exc) {
            return false;
        }
        for (StringMatcher m : matchers) {
            String tp = m.match(url);
            if (tp != null) {
                info.putHeader("Content-Type", tp);
                break;
            }
        }
        boolean cached = false;
        if (cell != null) {
            String etag = cell.getETag();
            if (etag != null) {
                String fullETag = "w/\"" + etag + '"';
                info.putHeader("Cache-Control", "public, max-age=" +
                    MAX_CACHE_AGE);
                info.putHeader("ETag", fullETag);
                String ifNoneMatch =
                    info.getClientData().getFieldValue("If-None-Match");
                if (ifNoneMatch.equals(fullETag)) cached = true;
            }
        }
        if (cached) {
            info.respond(304, "Not Modified", -1);
        } else {
            info.respond(200, "OK", size);
        }
        running.put(info, url);
        parent.assign(info, this);
        return true;
    }

    public void onOpen(RequestInfo info, ClientHandshake handshake) {
        String url = running.remove(info);
        if (url == null) url = info.getBase().getURL();
        FileCell cell;
        try {
            cell = getProducer().get(url, new Callback(info));
        } catch (FileNotFoundException exc) {
            cell = null;
        }
        if (cell != null) write(info, cell);
    }

    public void onError(RequestInfo info, Exception exc) {
        running.remove(info);
    }

    private static void write(RequestInfo info, FileCell cell) {
        ByteBuffer data = (cell == null) ? null : cell.getData();
        if (data != null && info.getBase().getCode() != 304) {
            info.getConnection().send(data);
        }
        info.getConnection().close();
    }

}
