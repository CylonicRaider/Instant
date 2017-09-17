package net.instant.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PluginManager {

    private final Map<String, Plugin> plugins;
    private PluginFetcher fetcher;
    private List<Plugin> order;

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
    public Plugin get(String name) {
        return plugins.get(name);
    }
    public Plugin fetch(String name) throws BadPluginException, IOException {
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
        order = null;
    }

    public List<Plugin> getOrder() {
        return order;
    }
    public List<Plugin> computeOrder() throws IntegrityException {
        if (order == null) {
            checkIntegrity();
            Set<Plugin> tempOrder = new LinkedHashSet<Plugin>();
            Deque<Plugin> stack = new LinkedList<Plugin>();
            for (Plugin p : plugins.values())
                traversePlugins(p, stack, tempOrder);
            order = Collections.unmodifiableList(new ArrayList<Plugin>(
                tempOrder));
        }
        return order;
    }
    private void traversePlugins(Plugin base, Deque<Plugin> stack,
            Set<Plugin> drain) throws IntegrityException {
        if (base == stack.peekFirst())
            throw new IntegrityException("Plugin " + base.getName() +
                " depends (transitively?) on itself");
        if (drain.contains(base))
            return;
        stack.add(base);
        for (String n : base.getRequirements())
            traversePlugins(get(n), stack, drain);
        drain.add(base);
        stack.remove(base);
    }

    public void checkIntegrity() throws IntegrityException {
        for (Plugin p : plugins.values()) {
            for (String n : p.getRequirements()) {
                if (get(n) == null)
                    throw new IntegrityException("Dependency " + n +
                        " of plugin " + p.getName() + " absent");
            }
            for (String n : p.getAttr(Plugin.BREAKS)) {
                if (get(n) != null)
                    throw new IntegrityException("Plugin " + p.getName() +
                        " conflicts with plugin " + n);
            }
        }
    }

}
