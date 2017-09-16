package net.instant.plugins;

public class StringAttribute extends PluginAttribute<String> {

    public StringAttribute(String name) {
        super(name);
    }

    public String parse(String value) {
        return value;
    }

}
