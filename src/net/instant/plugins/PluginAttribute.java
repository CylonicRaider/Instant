package net.instant.plugins;

import net.instant.api.PluginData;

public abstract class PluginAttribute<T> {

    private final String name;

    public PluginAttribute(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public T get(PluginAttributes attrs) {
        return attrs.get(this);
    }
    public T get(PluginData data) {
        return parse(data.getAttribute(name));
    }

    public abstract T parse(String value);

}
