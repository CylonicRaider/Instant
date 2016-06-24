package net.instant.util.fileprod;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class StringProducer implements Producer {

    private final Map<String, ByteBuffer> files;

    public StringProducer() {
        files = new HashMap<String, ByteBuffer>();
    }

    public synchronized void addFile(String name, ByteBuffer content) {
        files.put(name, content.asReadOnlyBuffer());
    }
    public synchronized void addFile(String name, String content) {
        byte[] data;
        try {
            data = content.getBytes("utf-8");
        } catch (UnsupportedEncodingException exc) {
            // Why must *this* be a checked one?
            throw new RuntimeException(exc);
        }
        addFile(name, ByteBuffer.wrap(data));
    }
    public synchronized void removeFile(String name) {
        files.remove(name);
    }

    public synchronized String[] listFiles() {
        return files.keySet().toArray(new String[0]);
    }
    public synchronized ByteBuffer getFile(String name) {
        return files.get(name);
    }

    public ProducerJob produce(String name) {
        final long pollTime = System.currentTimeMillis();
        final ByteBuffer content = files.get(name);
        if (content == null) return null;
        return new ProducerJob(name) {
            protected FileCell produce() {
                return new FileCell(getName(), content, pollTime);
            }
        };
    }

}
