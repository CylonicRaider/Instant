package net.instant.plugins;

import java.util.LinkedHashMap;
import java.util.Map;

public class PluginManager {

    private final Map<String, Plugin> plugins;

    public PluginManager() {
        plugins = new LinkedHashMap<String, Plugin>();
    }

    public Plugin get(String name) {
        return plugins.get(name);
    }
    public void add(Plugin p) {
        plugins.put(p.getName(), p);
    }

}
