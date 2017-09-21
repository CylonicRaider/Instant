package net.instant.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;

public class PluginAttributes {

    private final List<Attributes> bases;
    private Map<PluginAttribute<?>, Object> cache;

    public PluginAttributes(Attributes... bases) {
        List<Attributes> l = new ArrayList<Attributes>();
        for (Attributes a : bases) {
            if (a != null) l.add(a);
        }
        this.bases = Collections.unmodifiableList(l);
        this.cache = new HashMap<PluginAttribute<?>, Object>();
    }

    public List<Attributes> getBases() {
        return bases;
    }

    public String getRaw(Attributes.Name name) {
        if (name == null) return null;
        for (Attributes a : bases) {
            String ret = a.getValue(name);
            if (ret != null) return ret;
        }
        return null;
    }

    public String getRaw(String name) {
        return getRaw(new Attributes.Name(name));
    }

    public <T> T get(PluginAttribute<T> attr) {
        @SuppressWarnings("unchecked")
        T ret = (T) cache.get(attr);
        if (ret == null) {
            ret = attr.parse(getRaw(attr.getName()));
            cache.put(attr, ret);
        }
        return ret;
    }

}
