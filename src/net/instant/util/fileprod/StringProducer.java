package net.instant.util.fileprod;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import net.instant.util.Encodings;

public class StringProducer implements Producer {

    public class StringFileCell extends FileCell {

        public StringFileCell(String name, ByteBuffer content,
                              long created) {
            super(name, content, created);
        }

        public boolean isValid() {
            synchronized (StringProducer.this) {
                return (lastUpdates.get(getName()) == getCreated());
            }
        }

    }

    private final Map<String, ByteBuffer> files;
    private final Map<String, Long> lastUpdates;
    private final Map<String, List<ByteBuffer>> pendingAppends;

    public StringProducer() {
        files = new HashMap<String, ByteBuffer>();
        lastUpdates = new HashMap<String, Long>();
        pendingAppends = new HashMap<String, List<ByteBuffer>>();
    }

    protected synchronized void addFileEx(String name, ByteBuffer content) {
        files.put(name, content.asReadOnlyBuffer());
    }
    public synchronized void addFile(String name, ByteBuffer content) {
        addFileEx(name, content);
        pendingAppends.remove(name);
        lastUpdates.put(name, System.currentTimeMillis());
    }
    public void addFile(String name, String content) {
        addFile(name, Encodings.toBytes(content));
    }
    public synchronized void appendFile(String name, ByteBuffer content) {
        ByteBuffer oldContent = getCachedFile(name);
        if (oldContent == null) {
            addFile(name, content);
            return;
        }
        List<ByteBuffer> queue = pendingAppends.get(name);
        if (queue == null) {
            queue = new LinkedList<ByteBuffer>();
            pendingAppends.put(name, queue);
        }
        queue.add(content);
        lastUpdates.put(name, System.currentTimeMillis());
    }
    public void appendFile(String name, String content) {
        appendFile(name, Encodings.toBytes(content));
    }
    public synchronized void removeFile(String name) {
        files.remove(name);
        pendingAppends.remove(name);
    }

    public synchronized String[] listFiles() {
        return files.keySet().toArray(new String[0]);
    }
    public synchronized ByteBuffer getCachedFile(String name) {
        return files.get(name);
    }
    public synchronized ByteBuffer getFile(String name) {
        ByteBuffer oldData = getCachedFile(name);
        List<ByteBuffer> queue = pendingAppends.remove(name);
        if (queue == null) return oldData;
        int len = oldData.limit();
        for (ByteBuffer b : queue) len += b.limit();
        ByteBuffer newData = ByteBuffer.allocate(len);
        newData.put(oldData);
        for (ByteBuffer b : queue) newData.put(b);
        newData.flip();
        addFile(name, newData);
        return newData.asReadOnlyBuffer();
    }

    public ProducerJob produce(String name) {
        final ByteBuffer content;
        final Long pollTime;
        synchronized (this) {
            content = getFile(name);
            pollTime = lastUpdates.get(name);
        }
        if (content == null) return null;
        return new ProducerJob(name) {
            protected FileCell produce() {
                return new StringFileCell(getName(), content, pollTime);
            }
        };
    }

}
