package net.instant.util.fileprod;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FileProducer {

    private final FileCache cache;
    private final List<Producer> producers;
    private final Map<String, ProducerJob> pending;
    private final Executor pool;

    public FileProducer(FileCache cache) {
        this.cache = cache;
        this.producers = new LinkedList<Producer>();
        this.pending = new HashMap<String, ProducerJob>();
        this.pool = Executors.newCachedThreadPool();
    }
    public FileProducer() {
        this(new FileCache());
    }

    public FileCache getCache() {
        return cache;
    }
    public Executor getPool() {
        return pool;
    }

    public synchronized void addProducer(Producer p) {
        producers.add(p);
    }
    public synchronized Producer[] getProducers() {
        return producers.toArray(new Producer[producers.size()]);
    }

    protected synchronized ProducerJob produce(String name,
                                               ProducerJob.Callback cb) {
        ProducerJob job = pending.get(name);
        if (job != null) {
            if (cb != null) job.callback(cb);
            return job;
        }
        for (Producer p : producers) {
            job = p.produce(name);
            if (job == null) continue;
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
        return null;
    }

    public synchronized FileCell get(String name, ProducerJob.Callback cb)
            throws FileNotFoundException {
        FileCell res = cache.get(name);
        if (res == null) {
            if (produce(name, cb) == null)
                throw new FileNotFoundException("Path not found: " + name);
        }
        return res;
    }
    public FileCell get(String name) throws FileNotFoundException {
        return get(name, null);
    }

}
