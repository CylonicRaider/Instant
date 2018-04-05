package net.instant.util.fileprod;

import java.io.IOException;
import net.instant.util.Util;

public class QueryStrippingProducer implements Producer {

    public static class RenamedFileCell extends FileCell {

        private final FileCell source;

        public RenamedFileCell(String name, FileCell source) {
            super(name, source.getData(), source.getCreated());
            this.source = source;
        }

        public FileCell getSource() {
            return source;
        }

    }

    private final Producer inner;

    public QueryStrippingProducer(Producer inner) {
        this.inner = inner;
    }

    public Producer getInner() {
        return inner;
    }

    public ProducerJob produce(String name) {
        String lookupName = Util.splitQueryString(name)[0];
        final ProducerJob job = inner.produce(lookupName);
        if (job == null) return null;
        return new ProducerJob(name) {
            public FileCell produce() throws IOException {
                return new RenamedFileCell(getName(), job.produce());
            }
        };
    }

}
