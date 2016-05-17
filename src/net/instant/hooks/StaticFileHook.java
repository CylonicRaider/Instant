package net.instant.hooks;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.instant.InformationCollector;
import net.instant.InstantWebSocketServer;
import net.instant.util.FileCache;
import net.instant.util.Util;
import net.instant.ws.Draft_Raw;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;

public class StaticFileHook extends HookAdapter {

    private static class Callback implements FileCache.Callback {

        private final InformationCollector.Datum datum;
        private final WebSocket conn;

        public Callback(InformationCollector.Datum datum, WebSocket conn) {
            this.datum = datum;
            this.conn = conn;
        }

        public void operationCompleted(String name,
                                       FileCache.CacheCell cell) {
            write(datum, conn, cell);
        }

    }

    public interface ContentTypeMatcher {

        String match(String url);

    }

    public interface AliasMatcher {

        String match(String url);

    }

    public static class DefaultMatcher implements ContentTypeMatcher,
            AliasMatcher {

        private final Pattern pattern;
        private final String result;
        private final boolean dynamic;

        public DefaultMatcher(Pattern pattern, String result,
                              boolean dynamic) {
            this.pattern = pattern;
            this.result = result;
            this.dynamic = dynamic;
        }
        public DefaultMatcher(String pattern, String result,
                              boolean dynamic) {
            this(Pattern.compile(pattern), result, dynamic);
        }

        public String match(String url) {
            Matcher m = pattern.matcher(url);
            if (m.matches()) {
                if (dynamic) {
                    return RedirectHook.expand(m, result);
                } else {
                    return result;
                }
            } else {
                return null;
            }
        }

    }

    private final List<Pattern> resourceWhitelist;
    private final List<Pattern> cwdWhitelist;
    private final List<ContentTypeMatcher> matchers;
    private final List<AliasMatcher> aliases;
    private final Map<InformationCollector.Datum, String> running;
    private FileCache cache;

    public StaticFileHook(FileCache c) {
        cache = c;
        resourceWhitelist = new ArrayList<Pattern>();
        cwdWhitelist = new ArrayList<Pattern>();
        matchers = new ArrayList<ContentTypeMatcher>();
        aliases = new ArrayList<AliasMatcher>();
        running = new HashMap<InformationCollector.Datum, String>();
    }
    public StaticFileHook() {
        this(null);
    }

    public FileCache getCache() {
        return cache;
    }
    public void setCache(FileCache c) {
        cache = c;
    }

    public void whitelistResources(Pattern p) {
        resourceWhitelist.add(p);
    }
    public void whitelistResources(String p) {
        whitelistResources(Pattern.compile(p));
    }
    public void whitelistCWD(Pattern p) {
        cwdWhitelist.add(p);
    }
    public void whitelistCWD(String p) {
        whitelistCWD(Pattern.compile(p));
    }

    public void addContentTypeMatcher(ContentTypeMatcher m) {
        matchers.add(m);
    }
    public void matchContentType(Pattern p, String t) {
        addContentTypeMatcher(new DefaultMatcher(p, t, false));
    }
    public void matchContentType(String p, String t) {
        addContentTypeMatcher(new DefaultMatcher(p, t, false));
    }

    public void addAlias(AliasMatcher a) {
        aliases.add(a);
    }
    public void alias(Pattern p, String t) {
      addAlias(new DefaultMatcher(p, t, false));
    }
    public void alias(String p, String t) {
      addAlias(new DefaultMatcher(p, t, false));
    }
    public void alias(Pattern p, String t, boolean d) {
      addAlias(new DefaultMatcher(p, t, d));
    }
    public void alias(String p, String t, boolean d) {
      addAlias(new DefaultMatcher(p, t, d));
    }

    public boolean allowUnassigned() {
        return false;
    }

    public void postProcessRequest(InstantWebSocketServer parent,
                                   InformationCollector.Datum info,
                                   ClientHandshake request,
                                   ServerHandshakeBuilder response,
                                   Handshakedata eff_resp) {
        if (getCache() == null) return;
        if (! (parent.getEffectiveDraft(info) instanceof Draft_Raw)) return;
        if (! "GET".equals(info.getMethod())) return;
        processAs(parent, info, request, response,
                  request.getResourceDescriptor());
    }

    public boolean processAs(InstantWebSocketServer parent,
                             InformationCollector.Datum info,
                             ClientHandshake request,
                             ServerHandshakeBuilder response,
                             String urls) {
        for (AliasMatcher m : aliases) {
            String r = m.match(urls);
            if (r != null) {
                urls = r;
                break;
            }
        }
        return processAsEx(parent, info, request, response, urls);
    }

    public boolean processAsEx(InstantWebSocketServer parent,
                               InformationCollector.Datum info,
                               ClientHandshake request,
                               ServerHandshakeBuilder response,
                               String urls) {
        if (Util.matchWhitelist(urls, cwdWhitelist)) {
            File path = new File(urls.replaceFirst("^[/\\\\]+", ""));
            if (accept(parent, info, request, response, urls, path, null))
                return true;
        }
        if (Util.matchWhitelist(urls, resourceWhitelist)) {
            URL res = getClass().getResource(urls);
            if (res == null) {
                return false;
            } else if ("file".equals(res.getProtocol())) {
                File path = new File(res.getPath());
                if (accept(parent, info, request, response, urls,
                           path, null))
                    return true;
            } else {
                if (accept(parent, info, request, response, urls, null,
                           getClass().getResourceAsStream(urls)))
                    return true;
            }
        }
        return false;
    }

    protected boolean accept(InstantWebSocketServer parent,
                             InformationCollector.Datum info,
                             ClientHandshake request,
                             ServerHandshakeBuilder response,
                             String url, File path, InputStream is) {
        long size = -1;
        FileCache.CacheCell cell;
        if (is != null) {
            cell = getCache().get(url, is);
        } else if (path != null) {
            if (! path.isFile()) return false;
            cell = getCache().get(url, path);
            if (cell == null || cell.getContent() == null) {
                size = path.length();
            } else {
                size = cell.getSize();
            }
        } else {
            return false;
        }
        for (ContentTypeMatcher m : matchers) {
            String tp = m.match(url);
            if (tp != null) {
                response.put("Content-Type", tp);
                break;
            }
        }
        boolean cached = false;
        if (cell != null) {
            String etag = cell.getETag();
            if (etag != null) {
                String fullETag = "w/\"" + etag + '"';
                response.put("Cache-Control", "public, max-age=3600");
                response.put("ETag", fullETag);
                String ifNoneMatch = request.getFieldValue("If-None-Match");
                if (ifNoneMatch.equals(fullETag)) cached = true;
            }
        }
        if (cached) {
            info.setResponseInfo(response, (short) 304, "Not Modified", -1);
        } else {
            info.setResponseInfo(response, (short) 200, "OK", size);
        }
        running.put(info, url);
        parent.assign(info, this);
        return true;
    }

    public void onOpen(InformationCollector.Datum info,
                       WebSocket conn, ClientHandshake handshake) {
        String url = running.remove(info);
        if (url == null) url = info.getURL();
        FileCache.CacheCell cell = getCache().get(url,
                                                  new Callback(info, conn));
        if (cell != null) write(info, conn, cell);
    }

    private static void write(InformationCollector.Datum info,
                              WebSocket conn, FileCache.CacheCell cell) {
        ByteBuffer cnt = cell.getContent();
        if (cnt != null && info.getCode() != 304) {
            conn.send(cnt);
        }
        conn.close();
    }

}
