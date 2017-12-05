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
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;
import net.instant.api.API1;
import net.instant.util.Util;

public class PluginManager {

    private static final Logger LOGGER = Logger.getLogger("PluginManager");

    private final Map<String, Plugin> plugins;
    private final Queue<String> toLoad;
    private API1 api;
    private PluginFetcher fetcher;
    private PluginClassLoader classLoader;
    private List<Plugin> order;

    public PluginManager(API1 api) {
        this.plugins = new LinkedHashMap<String, Plugin>();
        this.toLoad = new LinkedList<String>();
        this.api = api;
        this.fetcher = new PluginFetcher(this);
        this.classLoader = new PluginClassLoader(
            getClass().getClassLoader());
    }

    public API1 getAPI() {
        return api;
    }
    public void setAPI(API1 a) {
        api = a;
    }

    public PluginFetcher getFetcher() {
        return fetcher;
    }
    public void setFetcher(PluginFetcher f) {
        fetcher = f;
    }

    public PluginClassLoader getClassLoader() {
        return classLoader;
    }
    public void setClassLoader(PluginClassLoader l) {
        classLoader = l;
    }

    public Collection<Plugin> getAll() {
        return Collections.unmodifiableCollection(plugins.values());
    }
    public Plugin get(String name) {
        return plugins.get(name);
    }
    public Plugin fetch(String name) throws PluginException, IOException {
        Plugin ret = plugins.get(name);
        if (ret == null && fetcher != null) {
            LOGGER.config("Fetching plugin " + name);
            ret = fetcher.fetch(name);
            if (ret == null)
                throw new NoSuchPluginException("Plugin " + name +
                                                " not found");
            add(ret);
            for (String n : ret.getDependencies()) {
                LOGGER.config(name + " requires " + n);
                fetch(n);
            }
        }
        return ret;
    }
    public void add(Plugin p) {
        plugins.put(p.getName(), p);
        order = null;
    }

    public void queueFetch(String name) {
        if (name == null) throw new NullPointerException();
        toLoad.add(name);
    }

    public Object getData(String name) throws IllegalArgumentException,
            IllegalStateException {
        Plugin p = get(name);
        if (p == null)
            throw new IllegalArgumentException("No such plugin: " + name);
        if (! p.isLoaded())
            throw new IllegalStateException("Plugin " + name +
                " not loaded yet");
        return p.getData();
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
            for (String n : p.getDependencies()) {
                if (get(n) == null)
                    throw new IntegrityException("Dependency " + n +
                        " of plugin " + p.getName() + " absent");
            }
            for (String n : Util.concat(p.getAttribute(Plugin.AFTER),
                                        p.getAttribute(Plugin.DEPENDS))) {
                if (get(n) == null) continue;
                deps.add(get(n));
            }
            for (String n : p.getAttribute(Plugin.BEFORE)) {
                Plugin o = get(n);
                if (o == null) continue;
                getDeps(ret, o).add(p);
            }
            for (String n : p.getAttribute(Plugin.BREAKS)) {
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

    protected void load() throws PluginException {
        for (;;) {
            String name = toLoad.poll();
            if (name == null) break;
            try {
                fetch(name);
            } catch (IOException exc) {
                throw new PluginException("Could not fetch plugin " + name,
                                          exc);
            }
        }
        for (Plugin p : computeOrder()) p.load();
    }
    protected void init() throws PluginException {
        for (Plugin p : computeOrder()) {
            LOGGER.info("Loading plugin " + p.getName() + "...");
            p.init();
        }
    }

    public void setup() throws PluginException {
        load();
        init();
    }

}
