package net.instant.plugins;

import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;

public class PluginAttributes {

    private final Attributes base;
    private Map<PluginAttribute<?>, Object> cache;

    public PluginAttributes(Attributes base) {
        this.base = base;
        this.cache = new HashMap<PluginAttribute<?>, Object>();
    }

    public <T> T get(PluginAttribute<T> attr) {
        @SuppressWarnings("unchecked")
        T ret = (T) cache.get(attr);
        if (ret == null) {
            ret = attr.parse(base.getValue(attr.getName()));
            cache.put(attr, ret);
        }
        return ret;
    }

}
