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
    private boolean done;
    private FileCell result;

    public ProducerJob(String name) {
        this.name = name;
        this.callbacks = new LinkedList<Callback>();
        this.done = false;
        this.result = null;
    }

    public String getName() {
        return name;
    }
    public boolean isDone() {
        return done;
    }
    public FileCell getResult() {
        return result;
    }

    public synchronized void callback(Callback cb) {
        if (done) {
            cb.fileProduced(name, result);
        } else {
            callbacks.add(cb);
        }
    }

    protected synchronized void runCallbacks(FileCell f) {
        done = true;
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
