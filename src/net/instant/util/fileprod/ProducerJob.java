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

    public void callback(Callback cb) {
        boolean run = false;
        synchronized (this) {
            if (done) {
                run = true;
            } else {
                callbacks.add(cb);
            }
        }
        if (run) cb.fileProduced(name, result);
    }
    protected synchronized Callback[] popCallbacks() {
        Callback[] ret = callbacks.toArray(new Callback[callbacks.size()]);
        callbacks.clear();
        return ret;
    }

    protected void runCallbacks(FileCell f) {
        Callback[] torun;
        synchronized (this) {
            done = true;
            result = f;
            torun = popCallbacks();
        }
        for (Callback cb : torun) {
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
