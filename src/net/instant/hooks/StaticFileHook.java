package net.instant.hooks;

import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.instant.api.ClientConnection;
import net.instant.api.RequestData;
import net.instant.api.RequestType;
import net.instant.api.ResponseBuilder;
import net.instant.util.Util;
import net.instant.util.config.Configuration;
import net.instant.util.fileprod.FileCell;
import net.instant.util.fileprod.FileProducer;
import net.instant.util.fileprod.ProducerJob;
import net.instant.util.stringmatch.ListStringMatcher;

public class StaticFileHook extends HookAdapter {

    private static final Logger LOGGER = Logger.getLogger("StaticFileHook");

    private static final String K_MAXAGE = "instant.http.maxCacheAge";
    public static final int DEFAULT_MAX_CACHE_AGE = 3600;

    private final ListStringMatcher aliases;
    private final ListStringMatcher contentTypes;
    private final int maxCacheAge;
    private FileProducer producer;

    public StaticFileHook(Configuration cfg, FileProducer p) {
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
        String[] rawParts = Util.splitQueryString(req.getPath());
        String basePath = aliases.match(rawParts[0]);
        if (basePath == null) basePath = rawParts[0];
        String fullPath = Util.joinQueryString(basePath, rawParts[1]);
        FileCell ent;
        try {
            ent = producer.get(fullPath);
        } catch (FileNotFoundException exc) {
            return false;
        }
        if (ent == null) {
            resp.respond(200, "OK", -1);
            req.getPrivateData().put("path", fullPath);
        } else {
            boolean cached = false;
            if (ent.getETag() != null) {
                String fullETag = "w/\"" + ent.getETag() + "\"";
                cached = fullETag.equals(req.getHeader("If-None-Match"));
                resp.addHeader("Cache-Control", "public, max-age=" +
                    maxCacheAge);
                resp.addHeader("ETag", fullETag);
            } else {
                // Prevent proxies from caching the non-revalidatable version.
                resp.addHeader("Cache-Control", "no-cache");
            }
            if (cached) {
                resp.respond(304, "Not Modified", -1);
                // Not registering path to not send response body.
            } else {
                resp.respond(200, "OK", ent.getSize());
                req.getPrivateData().put("path", fullPath);
            }
        }
        String contentType = contentTypes.match(basePath);
        if (contentType != null)
            resp.addHeader("Content-Type", contentType);
        return true;
    }

    public void onOpen(final ClientConnection conn) {
        String path = (String) conn.getPrivateData().get("path");
        if (path == null) {
            conn.getConnection().close();
            return;
        }
        try {
            producer.get(path, new ProducerJob.Callback() {
                public void fileProduced(String name, FileCell result) {
                    if (result != null) {
                        conn.getConnection().send(result.getData());
                    } else {
                        // Cannot do anything about failure now...
                        LOGGER.warning("Could not deliver static file " +
                            name + " although promised.");
                    }
                    conn.getConnection().close();
                }
            });
        } catch (FileNotFoundException exc) {
            LOGGER.log(Level.WARNING, "Static file " + path +
                " disappeared?!", exc);
            conn.getConnection().close();
        }
    }

}
