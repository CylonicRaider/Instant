package net.instant.util.fileprod;

import java.io.IOException;
import java.io.InputStream;

public class ResourceProducer implements Producer {

    private ClassLoader loader;

    public ResourceProducer(ClassLoader cl) {
        loader = cl;
    }
    public ResourceProducer() {
        this(null);
        // Cannot call methods until parent constructor has been invoked.
        setClassLoader(getClass().getClassLoader());
    }

    public ClassLoader getClassLoader() {
        return loader;
    }
    public void setClassLoader(ClassLoader cl) {
        loader = cl;
    }

    public ProducerJob produce(String name) {
        final long pollTime = System.currentTimeMillis();
        final InputStream is = loader.getResourceAsStream(
            name.replaceFirst("^/", ""));
        if (is == null) return null;
        return new ProducerJob(name) {
            protected FileCell produce() throws IOException {
                return new FileCell(getName(), is, pollTime);
            }
        };
    }

}
