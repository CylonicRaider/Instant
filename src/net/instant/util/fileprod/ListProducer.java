package net.instant.util.fileprod;

import java.util.ArrayList;
import java.util.List;

public class ListProducer implements Producer {

    private final List<Producer> children;

    public ListProducer() {
        children = new ArrayList<Producer>();
    }

    public synchronized Producer[] getChildren() {
        return children.toArray(new Producer[children.size()]);
    }
    public synchronized void add(Producer p) {
        children.add(p);
    }
    public synchronized void remove(Producer p) {
        children.remove(p);
    }

    public ProducerJob produce(String name) {
        for (Producer p : getChildren()) {
            ProducerJob res = p.produce(name);
            if (res != null) return res;
        }
        return null;
    }

}
