package net.instant.util.fileprod;

import java.io.IOException;
import net.instant.api.FileGenerator;
import net.instant.api.FileInfo;

public class APIProducer implements Producer {

    private final FileGenerator base;

    public APIProducer(FileGenerator base) {
        this.base = base;
    }

    public FileGenerator getBase() {
        return base;
    }

    public ProducerJob produce(String name) {
        try {
            if (! base.hasFile(name)) return null;
        } catch (IOException exc) {
            // Uhh... That counts as "no".
            return null;
        }
        return new ProducerJob(name) {
            public FileCell produce() throws IOException {
                final FileInfo info = base.generateFile(getName());
                return new FileCell(info.getName(), info.getData(),
                                    info.getCreated()) {
                    public boolean isValid() {
                        return info.isValid();
                    }
                };
            }
        };
    }

}
