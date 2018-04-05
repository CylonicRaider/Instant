package net.instant.util.fileprod;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class FileProducer {

    private static final Logger LOGGER = Logger.getLogger("FileProducer");

    public static int GC_INTERVAL = 3600000;

    private final Map<String, ProducerJob> pending;
    private final Executor pool;
    private ListProducer producer;
    private FileCache cache;

    public FileProducer(ListProducer producer, FileCache cache) {
        this.pending = new HashMap<String, ProducerJob>();
        this.pool = Executors.newCachedThreadPool();
        this.producer = producer;
        this.cache = cache;
    }
    public FileProducer() {
        this(new ListProducer(), new FileCache());
    }

    public Executor getPool() {
        return pool;
    }

    public ListProducer getProducer() {
        return producer;
    }
    public void setProducer(ListProducer p) {
        producer = p;
    }

    public FileCache getCache() {
        return cache;
    }
    public void setCache(FileCache c) {
        cache = c;
    }

    protected synchronized ProducerJob produce(String name,
                                               ProducerJob.Callback cb) {
        ProducerJob job = pending.get(name);
        if (job != null) {
            if (cb != null) job.callback(cb);
            return job;
        }
        if (producer == null) return null;
        job = producer.produce(name);
        if (job == null) return null;
        job.callback(new ProducerJob.Callback() {
            public void fileProduced(String name, FileCell f) {
                synchronized (FileProducer.this) {
                    cache.add(f);
                    pending.remove(name);
                }
            }
        });
        if (cb != null) job.callback(cb);
        pending.put(name, job);
        pool.execute(job);
        return job;
    }

    public FileCell get(String name, ProducerJob.Callback cb)
            throws FileNotFoundException {
        FileCell res;
        synchronized (this) {
            res = cache.get(name);
            if (res == null) {
                if (produce(name, cb) == null)
                    throw new FileNotFoundException("Path not found: " +
                        name);
            }
        }
        if (cb != null && res != null)
            cb.fileProduced(name, res);
        return res;
    }
    public FileCell get(String name) throws FileNotFoundException {
        return get(name, null);
    }

    public Runnable getGCTask() {
        return new Runnable() {
            public void run() {
                LOGGER.fine("Garbage-collecting static files...");
                cache.gc();
            }
        };
    }

}
