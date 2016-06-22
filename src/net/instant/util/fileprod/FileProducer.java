package net.instant.util.fileprod;

import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import net.instant.util.Util;

public class FileProducer {

    private final FileCache cache;
    private final List<Pattern> whitelist;
    private final List<Producer> producers;
    private final Executor pool;

    public FileProducer(FileCache cache) {
        this.cache = cache;
        this.whitelist = new LinkedList<Pattern>();
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

    public synchronized void whitelist(Pattern p) {
        whitelist.add(p);
    }
    public synchronized Pattern[] getWhitelist() {
        return whitelist.toArray(new Pattern[whitelist.size()]);
    }
    public synchronized boolean checkWhitelist(String name) {
        return Util.matchWhitelist(name, whitelist);
    }

    public synchronized void addProducer(Producer p) {
        producers.add(p);
    }
    public synchronized Producer[] getProducers() {
        return producers.toArray(new Producer[producers.size()]);
    }

    protected synchronized ProducerJob produce(String name) {
        if (! checkWhitelist(name)) return null;
        for (Producer p : producers) {
            ProducerJob job = p.produce(name);
            if (job == null) continue;
            getPool().execute(job);
            return job;
        }
        return null;
    }

    public FileCell get(String name, ProducerJob.Callback cb)
            throws FileNotFoundException {
        FileCell res = cache.get(name);
        if (res == null) {
            ProducerJob job = produce(name);
            if (job == null)
                throw new FileNotFoundException("Path not found: " + name);
            if (cb != null) job.addCallback(cb);
        }
        return res;
    }
    public FileCell get(String name) throws FileNotFoundException {
        return get(name, null);
    }

}
