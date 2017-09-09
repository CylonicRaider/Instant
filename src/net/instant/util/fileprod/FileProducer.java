package net.instant.util.fileprod;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FileProducer {

    private final Map<String, ProducerJob> pending;
    private final Executor pool;
    private FileCache cache;
    private Producer producer;

    public FileProducer(FileCache cache, Producer producer) {
        this.pending = new HashMap<String, ProducerJob>();
        this.pool = Executors.newCachedThreadPool();
        this.cache = cache;
        this.producer = producer;
    }
    public FileProducer() {
        this(new FileCache(), null);
    }

    public Executor getPool() {
        return pool;
    }

    public FileCache getCache() {
        return cache;
    }
    public void setCache(FileCache c) {
        cache = c;
    }

    public Producer getProducer() {
        return producer;
    }
    public void setProducer(Producer p) {
        producer = p;
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
                    getCache().add(f);
                    pending.remove(name);
                }
            }
        });
        if (cb != null) job.callback(cb);
        pending.put(name, job);
        getPool().execute(job);
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

}
