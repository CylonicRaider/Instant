package net.instant.plugins;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PluginManager {

    private final Map<String, Plugin> plugins;
    private PluginFetcher fetcher;

    public PluginManager() {
        plugins = new LinkedHashMap<String, Plugin>();
        fetcher = new PluginFetcher();
    }

    public PluginFetcher getFetcher() {
        return fetcher;
    }
    public void setFetcher(PluginFetcher f) {
        fetcher = f;
    }

    public Collection<Plugin> getAll() {
        return Collections.unmodifiableCollection(plugins.values());
    }
    public Plugin getRaw(String name) {
        return plugins.get(name);
    }
    public Plugin get(String name) throws BadPluginException, IOException {
        Plugin ret = plugins.get(name);
        if (ret == null && fetcher != null) {
            ret = fetcher.fetch(name);
            add(ret);
            for (String n : ret.getRequirements()) get(n);
        }
        return ret;
    }
    public void add(Plugin p) {
        plugins.put(p.getName(), p);
    }

}
