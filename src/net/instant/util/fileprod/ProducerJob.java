package net.instant.util.fileprod;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public abstract class ProducerJob implements Runnable {

    public interface Callback {

        void fileProduced(String name, FileCell f);

    }

    private final String name;
    private final List<Callback> callbacks;

    public ProducerJob(String name) {
        this.name = name;
        this.callbacks = new LinkedList<Callback>();
    }

    public String getName() {
        return name;
    }

    public synchronized void addCallback(Callback cb) {
        callbacks.add(cb);
    }

    protected synchronized void runCallbacks(FileCell f) {
        for (Callback cb : callbacks) {
            cb.fileProduced(name, f);
        }
    }

    public void run() {
        FileCell res = null;
        try {
            res = produce();
        } catch (IOException exc) {
            exc.printStackTrace();
        } finally {
            runCallbacks(res);
        }
    }

    protected abstract FileCell produce() throws IOException;

}
