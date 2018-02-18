package net.instant.hooks;

import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.instant.api.ClientConnection;
import net.instant.api.RequestData;
import net.instant.api.RequestType;
import net.instant.api.ResponseBuilder;
import net.instant.util.Configuration;
import net.instant.util.ListStringMatcher;
import net.instant.util.Util;
import net.instant.util.fileprod.FileCell;
import net.instant.util.fileprod.FileProducer;
import net.instant.util.fileprod.ProducerJob;

public class StaticFileHook extends HookAdapter {

    private static final String K_MAXAGE = "instant.http.maxCacheAge";
    public static final int DEFAULT_MAX_CACHE_AGE = 3600;

    private final Map<RequestData, String> paths;
    private final ListStringMatcher aliases;
    private final ListStringMatcher contentTypes;
    private final int maxCacheAge;
    private FileProducer producer;

    public StaticFileHook(Configuration cfg, FileProducer p) {
        paths = Collections.synchronizedMap(
            new HashMap<RequestData, String>());
        aliases = new ListStringMatcher();
        contentTypes = new ListStringMatcher();
        int age;
        try {
            age = Integer.parseInt(cfg.get(K_MAXAGE));
        } catch (NumberFormatException exc) {
            age = DEFAULT_MAX_CACHE_AGE;
        }
        maxCacheAge = age;
        producer = p;
    }
    public StaticFileHook() {
        this(Configuration.DEFAULT, new FileProducer());
    }

    public ListStringMatcher getAliases() {
        return aliases;
    }

    public ListStringMatcher getContentTypes() {
        return contentTypes;
    }

    public FileProducer getProducer() {
        return producer;
    }
    public void setProducer(FileProducer p) {
        producer = p;
    }

    public boolean evaluateRequest(RequestData req, ResponseBuilder resp) {
        if (producer == null || req.getRequestType() != RequestType.HTTP ||
                ! req.getMethod().equals("GET"))
            return false;
        String rawPath = Util.trimQuery(req.getPath());
        String path = aliases.match(rawPath);
        if (path == null) path = rawPath;
        FileCell ent;
        try {
            ent = producer.get(path);
        } catch (FileNotFoundException exc) {
            return false;
        }
        if (ent == null) {
            resp.respond(200, "OK", -1);
            paths.put(req, path);
        } else {
            boolean cached = false;
            if (ent.getETag() != null) {
                String fullETag = "w/\"" + ent.getETag() + "\"";
                cached = fullETag.equals(req.getHeader("If-None-Match"));
                resp.addHeader("Cache-Control", "public, max-age=" +
                    maxCacheAge);
                resp.addHeader("ETag", fullETag);
            }
            if (cached) {
                resp.respond(304, "Not Modified", ent.getSize());
                // Not registering path to not send response body.
            } else {
                resp.respond(200, "OK", ent.getSize());
                paths.put(req, path);
            }
        }
        String contentType = contentTypes.match(path);
        if (contentType != null)
            resp.addHeader("Content-Type", contentType);
        return true;
    }

    public void onOpen(final ClientConnection conn) {
        try {
            String path = paths.remove(conn);
            if (path == null) {
                conn.getConnection().close();
                return;
            }
            producer.get(path, new ProducerJob.Callback() {
                public void fileProduced(String name, FileCell result) {
                    // Cannot do anything about failure now...
                    if (result != null)
                        conn.getConnection().send(result.getData());
                    conn.getConnection().close();
                }
            });
        } catch (FileNotFoundException exc) {
            // Should not happen
            throw new RuntimeException(exc);
        }
    }

}
