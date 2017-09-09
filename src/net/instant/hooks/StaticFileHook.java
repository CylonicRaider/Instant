package net.instant.hooks;

import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.instant.api.ClientConnection;
import net.instant.api.RequestData;
import net.instant.api.ResponseBuilder;
import net.instant.util.ListStringMatcher;
import net.instant.util.fileprod.FileCell;
import net.instant.util.fileprod.FileProducer;
import net.instant.util.fileprod.ProducerJob;

public class StaticFileHook extends HookAdapter {

    public static final int MAX_CACHE_AGE = 3600;

    private final Map<RequestData, String> paths;
    private final ListStringMatcher aliases;
    private final ListStringMatcher contentTypes;
    private FileProducer producer;

    public StaticFileHook(FileProducer p) {
        paths = Collections.synchronizedMap(
            new HashMap<RequestData, String>());
        aliases = new ListStringMatcher();
        contentTypes = new ListStringMatcher();
        producer = p;
    }
    public StaticFileHook() {
        this(new FileProducer());
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
        if (producer == null) return false;
        String path = aliases.match(req.getPath());
        if (path == null) path = req.getPath();
        FileCell ent;
        try {
            ent = producer.get(path);
        } catch (FileNotFoundException exc) {
            return false;
        }
        if (ent == null) {
            resp.respond(200, "OK", -1);
        } else {
            boolean cached = false;
            if (ent.getETag() != null) {
                String fullETag = "w/\"" + ent.getETag() + "\"";
                cached = fullETag.equals(req.getHeader("If-None-Match"));
                resp.addHeader("Cache-Control", "public, max-age=" +
                    MAX_CACHE_AGE);
                resp.addHeader("ETag", fullETag);
            }
            if (cached) {
                resp.respond(304, "Not Modified", ent.getSize());
            } else {
                resp.respond(200, "OK", ent.getSize());
            }
        }
        String contentType = contentTypes.match(path);
        if (contentType != null)
            resp.addHeader("Content-Type", contentType);
        paths.put(req, path);
        return true;
    }

    public void onOpen(final ClientConnection conn) {
        try {
            producer.get(paths.remove(conn), new ProducerJob.Callback() {
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
