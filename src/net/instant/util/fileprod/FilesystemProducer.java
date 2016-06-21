package net.instant.util.fileprod;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class FilesystemProducer implements Producer {

    public ProducerJob produce(String name) {
        final File path = new File(name.replaceFirst("^[/\\\\]+", ""));
        if (! path.isFile()) return null;
        return new ProducerJob(name) {
            protected FileCell produce() throws IOException {
                return new FilesystemFileCell(getName(), path);
            }
        };
    }

}
