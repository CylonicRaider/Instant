package net.instant.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* This class took me three re-designs. In the end, I've produced something
 * both elegant and working. */
public class FileCache {

    public static final int BASE_BUFFER_SIZE = 4096;

    public class CacheCell {

        private final String name;
        private final File path;
        private final ByteBuffer content;
        private final long lastPoll;
        private String etag;

        protected CacheCell(String name, File path, ByteBuffer content,
                            long lastPoll) {
            this.name = name;
            this.path = path;
            this.content = content;
            this.lastPoll = lastPoll;
        }

        public String getName() {
            return name;
        }
        public File getPath() {
            return path;
        }
        protected ByteBuffer getRawContent() {
            return content;
        }
        public ByteBuffer getContent() {
            return (content != null) ? content.asReadOnlyBuffer() : null;
        }
        public long getLastPoll() {
            return lastPoll;
        }

        public String getETag() {
            if (etag == null) {
                MessageDigest d;
                try {
                    d = MessageDigest.getInstance("MD5");
                } catch (NoSuchAlgorithmException exc) {
                    // SRSLY?
                    return null;
                }
                d.update(Util.toBytes(lastPoll));
                if (content == null) {
                    d.update((byte) 0);
                } else {
                    d.update((byte) 1);
                    d.update(getContent());
                }
                etag = Util.toHex(d.digest());
            }
            return etag;
        }

        public int getSize() {
            return (content != null) ? content.limit() : -1;
        }

        public boolean isValid() {
            if (getPath() == null) return true;
            return (getPath().lastModified() < getLastPoll());
        }

        public FileCache getParent() {
            return FileCache.this;
        }

    }

    protected class ReaderTask implements Runnable {

        private final String name;
        private final File path;
        private final InputStream input;

        public ReaderTask(String name, File path, InputStream input) {
            this.name = name;
            this.path = path;
            this.input = input;
        }

        public String getName() {
            return name;
        }
        public File getPath() {
            return path;
        }
        public InputStream getInput() {
            return input;
        }

        public void run() {
            CacheCell ret = null;
            long pollTime = System.currentTimeMillis();
            try {
                ByteBuffer bbuf;
                if (input == null) {
                    bbuf = null;
                } else {
                    byte[] buf = new byte[BASE_BUFFER_SIZE];
                    int idx = 0;
                    for (;;) {
                        int rd = input.read(buf, idx, buf.length - idx);
                        if (rd < 0) break;
                        idx += rd;
                        if (idx == buf.length) {
                            byte[] nbuf = new byte[idx * 2];
                            System.arraycopy(buf, 0, nbuf, 0, idx);
                            buf = nbuf;
                        }
                    }
                    bbuf = ByteBuffer.wrap(buf, 0, idx);
                }
                ret = new CacheCell(name, path, bbuf, pollTime);
            } catch (Exception e) {
                ret = new CacheCell(name, path, null, pollTime);
                throw new RuntimeException(e);
            } finally {
                completed(this, ret);
            }
        }

    }

    protected class CallbackTask implements Runnable {

        private final String name;
        private final CacheCell cell;
        private final List<Callback> callbacks;

        public CallbackTask(String name, CacheCell cell,
                            List<Callback> callbacks) {
            this.name = name;
            this.cell = cell;
            if (callbacks == null) {
                this.callbacks = Collections.emptyList();
            } else {
                this.callbacks = callbacks;
            }
        }

        public String getName() {
            return name;
        }
        public CacheCell getCell() {
            return cell;
        }
        public List<Callback> getCallbacks() {
            return Collections.unmodifiableList(callbacks);
        }

        public void run() {
            for (Callback c : callbacks) {
                c.operationCompleted(name, cell);
            }
        }

    }

    public interface Callback {

        void operationCompleted(String name, CacheCell cell);

    }

    private final Map<String, CacheCell> cells;
    private final Map<String, List<Callback>> pending;
    private final ExecutorService pool;

    public FileCache() {
        cells = new HashMap<String, CacheCell>();
        pending = new HashMap<String, List<Callback>>();
        pool = Executors.newCachedThreadPool();
    }

    protected synchronized void addCallback(String name, Callback cb) {
        List<Callback> l = pending.get(name);
        if (l == null) {
            l = new LinkedList<Callback>();
            pending.put(name, l);
        }
        if (cb != null) l.add(cb);
    }
    protected synchronized void addCallback(String name) {
        if (pending.get(name) == null) {
            pending.put(name, new LinkedList<Callback>());
        }
    }
    protected synchronized void runCallbacks(String name) {
        CacheCell cell = get(name);
        List<Callback> l = pending.remove(name);
        getPool().execute(new CallbackTask(name, cell, l));
    }

    protected synchronized void completed(ReaderTask task,
                                          CacheCell cell) {
        cells.put(task.getName(), cell);
        runCallbacks(task.getName());
    }

    public synchronized CacheCell get(String name) {
        return cells.get(name);
    }
    public synchronized CacheCell get(String name, Callback callback) {
        CacheCell cell = get(name);
        if (isValid(cell)) return cell;
        addCallback(name, callback);
        return null;
    }
    public synchronized CacheCell get(String name, File path, Callback cb) {
        CacheCell cell = get(name);
        if (isValid(cell)) return cell;
        addCallback(name, cb);
        put(name, path);
        return null;
    }
    public CacheCell get(String name, File path) {
        return get(name, path, null);
    }
    public synchronized CacheCell get(String name, InputStream input,
                                      Callback callback) {
        CacheCell cell = get(name);
        if (isValid(cell)) return cell;
        addCallback(name, callback);
        put(name, null, input);
        return null;
    }
    public CacheCell get(String name, InputStream input) {
        return get(name, input, null);
    }

    protected boolean isValid(CacheCell cell) {
        return (cell == null) ? false : cell.isValid();
    }

    public void put(String name, File path) {
        try {
            put(name, path, new FileInputStream(path));
        } catch (FileNotFoundException exc) {
            put(name, path, null);
        }
    }
    public void put(String name, File path, InputStream input) {
        addCallback(name);
        getPool().execute(new ReaderTask(name, path, input));
    }

    public ExecutorService getPool() {
        return pool;
    }

}
