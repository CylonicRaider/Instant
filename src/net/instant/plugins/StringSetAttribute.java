package net.instant.plugins;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.instant.util.Util;

public class StringSetAttribute extends PluginAttribute<Set<String>> {

    public StringSetAttribute(String name) {
        super(name);
    }

    public Set<String> parse(String rawValue) {
        return new HashSet<String>(Util.parseCommaList(rawValue));
    }

}
