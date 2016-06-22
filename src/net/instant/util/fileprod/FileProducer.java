package net.instant.util.fileprod;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FileProducer {

    private final FileCache cache;
    private final List<Producer> producers;
    private final Executor pool;

    public FileProducer(FileCache cache) {
        this.cache = cache;
        this.producers = new LinkedList<Producer>();
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

    protected synchronized ProducerJob produce(String name) {
        for (Producer p : producers) {
            ProducerJob job = p.produce(name);
            if (job == null) continue;
            getPool().execute(job);
            return job;
        }
        return null;
    }

    public FileCell get(String name, ProducerJob.Callback cb) {
        FileCell res = cache.get(name);
        if (res == null) {
            ProducerJob job = produce(name);
            if (cb != null) job.addCallback(cb);
        }
        return res;
    }
    public FileCell get(String name) {
        return get(name, null);
    }

}
