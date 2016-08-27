package net.instant.util.fileprod;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import net.instant.util.Util;

public class StringProducer implements Producer {

    private final Map<String, ByteBuffer> files;

    public StringProducer() {
        files = new HashMap<String, ByteBuffer>();
    }

    public synchronized void addFile(String name, ByteBuffer content) {
        files.put(name, content.asReadOnlyBuffer());
    }
    public void addFile(String name, String content) {
        addFile(name, Util.toBytes(content));
    }
    public synchronized void appendFile(String name, ByteBuffer content) {
        ByteBuffer oldContent = getFile(name);
        ByteBuffer newContent = ByteBuffer.allocate(oldContent.limit() +
            content.limit());
        newContent.put(oldContent);
        newContent.put(content);
        newContent.flip();
        addFile(name, newContent);
    }
    public void appendFile(String name, String content) {
        appendFile(name, Util.toBytes(content));
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
