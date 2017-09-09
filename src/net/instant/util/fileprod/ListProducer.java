package net.instant.util.fileprod;

import java.util.ArrayList;
import java.util.List;

public class ListProducer implements Producer {

    private final List<Producer> children;

    public ListProducer() {
        children = new ArrayList<Producer>();
    }

    public synchronized List<Producer> getChildren() {
        return new ArrayList<Producer>(children);
    }
    public synchronized void addChild(Producer p) {
        children.add(p);
    }
    public synchronized void removeChild(Producer p) {
        children.remove(p);
    }

    public ProducerJob produce(String name) {
        List<Producer> ch = getChildren();
        for (Producer p : ch) {
            ProducerJob res = p.produce(name);
            if (res != null) return res;
        }
        return null;
    }

}
