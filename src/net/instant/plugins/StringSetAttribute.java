package net.instant.plugins;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.instant.util.Formats;

public class StringSetAttribute extends PluginAttribute<Set<String>> {

    public StringSetAttribute(String name) {
        super(name);
    }

    public Set<String> parse(String rawValue) {
        if (rawValue == null) return null;
        return new HashSet<String>(Formats.parseCommaList(rawValue));
    }

}
