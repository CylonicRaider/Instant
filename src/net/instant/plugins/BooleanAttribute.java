package net.instant.plugins;

import net.instant.util.Util;

public class BooleanAttribute extends PluginAttribute<Boolean> {

    public BooleanAttribute(String name) {
        super(name);
    }

    public Boolean parse(String value) {
        return Util.isTrue(value);
    }

}
