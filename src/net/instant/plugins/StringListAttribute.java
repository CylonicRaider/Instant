package net.instant.plugins;

import java.util.List;
import net.instant.util.Util;

public class StringListAttribute extends PluginAttribute<List<String>> {

    public StringListAttribute(String name) {
        super(name);
    }

    public List<String> parse(String rawValue) {
        return Util.parseCommaList(rawValue);
    }

}
