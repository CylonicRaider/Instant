package net.instant.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.instant.util.Util;

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
            Map<Plugin, Set<Plugin>> deps = depencyMap();
            Set<Plugin> tempOrder = new LinkedHashSet<Plugin>();
            Deque<Plugin> stack = new LinkedList<Plugin>();
            for (Plugin p : plugins.values())
                traversePlugins(p, deps, stack, tempOrder);
            order = Collections.unmodifiableList(new ArrayList<Plugin>(
                tempOrder));
        }
        return order;
    }

    private Set<Plugin> getDeps(Map<Plugin, Set<Plugin>> data, Plugin key) {
        Set<Plugin> ret = data.get(key);
        if (ret == null) {
            ret = new LinkedHashSet<Plugin>();
            data.put(key, ret);
        }
        return ret;
    }
    private Map<Plugin, Set<Plugin>> depencyMap() throws IntegrityException {
        Map<Plugin, Set<Plugin>> ret = new HashMap<Plugin, Set<Plugin>>();
        for (Plugin p : plugins.values()) {
            Set<Plugin> deps = getDeps(ret, p);
            for (String n : p.getRequirements()) {
                if (get(n) != null)
                    throw new IntegrityException("Dependency " + n +
                        " of plugin " + p.getName() + " absent");
            }
            for (String n : Util.concat(p.getAttr(Plugin.AFTER),
                                        p.getAttr(Plugin.DEPENDS))) {
                if (get(n) == null) continue;
                deps.add(get(n));
            }
            for (String n : p.getAttr(Plugin.BEFORE)) {
                Plugin o = get(n);
                if (o == null) continue;
                getDeps(ret, o).add(p);
            }
            for (String n : p.getAttr(Plugin.BREAKS)) {
                if (get(n) != null)
                    throw new IntegrityException("Plugin " + p.getName() +
                        " conflicts with plugin " + n);
            }
        }
        return ret;
    }
    private void traversePlugins(Plugin base, Map<Plugin, Set<Plugin>> deps,
            Deque<Plugin> stack, Set<Plugin> drain)
            throws IntegrityException {
        if (base == stack.peekFirst())
            throw new IntegrityException("Plugin " + base.getName() +
                " is (transitively?) ordered before itself");
        if (drain.contains(base))
            return;
        stack.add(base);
        for (Plugin p : deps.get(base))
            traversePlugins(p, deps, stack, drain);
        drain.add(base);
        stack.remove(base);
    }

}