package net.instant.util.fileprod;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import net.instant.util.Util;

public abstract class WhitelistProducer implements Producer {

    private final List<Pattern> whitelist;

    public WhitelistProducer() {
        whitelist = new LinkedList<Pattern>();
    }

    public synchronized void whitelist(Pattern p) {
        whitelist.add(p);
    }
    public void whitelist(String p) {
        whitelist(Pattern.compile(p));
    }
    public synchronized Pattern[] getWhitelist() {
        return whitelist.toArray(new Pattern[whitelist.size()]);
    }
    public synchronized boolean checkWhitelist(String name) {
        return Util.matchWhitelist(name, whitelist);
    }

    public ProducerJob produce(String name) {
        if (! checkWhitelist(name)) return null;
        return produceInner(name);
    }

    protected abstract ProducerJob produceInner(String name);

}
