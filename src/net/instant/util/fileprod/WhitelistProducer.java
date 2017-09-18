package net.instant.util.fileprod;

public class WhitelistProducer extends AbstractWhitelistProducer {

    private Producer child;

    public WhitelistProducer(Producer child) {
        this.child = child;
    }
    public WhitelistProducer() {
        this(null);
    }

    public Producer getChild() {
        return child;
    }
    public void setChild(Producer p) {
        child = p;
    }

    protected ProducerJob produceInner(String name) {
        return child.produce(name);
    }

}
