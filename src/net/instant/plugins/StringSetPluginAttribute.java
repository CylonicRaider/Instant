package net.instant.plugins;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class StringSetPluginAttribute extends PluginAttribute<Set<String>> {

    public StringSetPluginAttribute(String name) {
        super(name);
    }

    public Set<String> parse(String value) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(
            Arrays.asList(value.split(","))));
    }

}
