package net.instant.util.fileprod;

import java.io.IOException;
import java.io.InputStream;

public class ResourceProducer implements Producer {

    public ProducerJob produce(String name) {
        final long pollTime = System.currentTimeMillis();
        final InputStream is = getClass().getResourceAsStream(name);
        if (is == null) return null;
        return new ProducerJob(name) {
            protected FileCell produce() throws IOException {
                return new FileCell(getName(), is, pollTime);
            }
        };
    }

}
