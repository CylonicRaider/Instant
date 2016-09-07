package net.instant.plugins;

public abstract class PluginAttribute<T> {

    private final String name;

    public PluginAttribute(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract T parse(String source);

}
