package net.instant.hooks;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.instant.api.ClientConnection;
import net.instant.api.RequestData;
import net.instant.api.ResponseBuilder;
import net.instant.util.fileprod.FileCell;
import net.instant.util.fileprod.Producer;
import net.instant.util.fileprod.ProducerJob;

public class StaticFileHook extends HookAdapter {

    private final Map<RequestData, ProducerJob> pending;
    private Producer producer;

    public StaticFileHook(Producer p) {
        pending = Collections.synchronizedMap(
            new HashMap<RequestData, ProducerJob>());
        producer = p;
    }
    public StaticFileHook() {
        this(null);
    }

    public Producer getProducer() {
        return producer;
    }
    public void setProducer(Producer p) {
        producer = p;
    }

    public boolean evaluateRequest(RequestData req, ResponseBuilder resp) {
        if (producer == null) return false;
        ProducerJob job = producer.produce(req.getPath());
        if (job == null) return false;
        resp.respond(200, "OK", -1);
        pending.put(req, job);
        new Thread(job).start();
        return true;
    }

    public void onOpen(final ClientConnection conn) {
        pending.remove(conn).callback(new ProducerJob.Callback() {
            public void fileProduced(String name, FileCell result) {
                // Cannot do anything about failure now...
                if (result != null)
                    conn.getConnection().send(result.getData());
                conn.getConnection().close();
            }
        });
    }

}
