package net.instant.plugins;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class PluginManager {

    private final Map<String, Plugin> plugins;
    private PluginFetcher fetcher;

    public PluginManager() {
        plugins = new LinkedHashMap<String, Plugin>();
    }

    public PluginFetcher getFetcher() {
        return fetcher;
    }
    public void setFetcher(PluginFetcher f) {
        fetcher = f;
    }

    public Plugin get(String name) {
        return plugins.get(name);
    }
    public void add(Plugin p) {
        plugins.put(p.getName(), p);
    }

    public Plugin fetch(String name)
            throws IOException, NoSuchPluginException {
        Plugin p = get(name);
        if (p != null) return p;
        p = fetcher.fetch(this, name);
        if (p == null) throw new NoSuchPluginException(name);
        add(p);
        return p;
    }

}
