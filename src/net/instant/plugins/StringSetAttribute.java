package net.instant.plugins;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class StringSetAttribute extends PluginAttribute<Set<String>> {

    public StringSetAttribute(String name) {
        super(name);
    }

    public Set<String> parse(String value) {
        if (value == null) return Collections.emptySet();
        return Collections.unmodifiableSet(new LinkedHashSet<String>(
            Arrays.asList(value.split(","))));
    }

}
