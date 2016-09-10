package net.instant.plugins;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PluginManager {

    private final Map<String, Plugin> plugins;
    private final Set<PluginGroup> groups;
    private PluginFetcher fetcher;

    public PluginManager() {
        plugins = new LinkedHashMap<String, Plugin>();
        groups = new HashSet<PluginGroup>();
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
    public void add(Plugin p) {
        plugins.put(p.getName(), p);
    }

    public Plugin fetch(String name)
            throws IOException, PluginException {
        Plugin p = get(name);
        if (p != null) return p;
        p = fetcher.fetch(this, name);
        if (p == null) throw new NoSuchPluginException(name);
        add(p);
        for (String n : p.getDependencies()) fetch(n);
        return p;
    }

    public Set<PluginGroup> getGroups() {
        return Collections.unmodifiableSet(groups);
    }
    public void addGroup(PluginGroup g) {
        groups.add(g);
    }
    public void removeGroup(PluginGroup g) {
        groups.remove(g);
    }

    protected void normalizeIndividualConstraints()
            throws PluginConflictException {
        /* Ensure that all inter-plugin constraints are mutual and that no
         * plugins break each other or have (temporal) dependency loops. */
        for (Plugin p : getAll()) {
            for (Plugin q : getAll()) {
                Constraint pq = p.getConstraint(q);
                Constraint qp = q.getConstraint(p).flip();
                if (! pq.isCompatible(qp))
                    throw new MutualPluginConflictException(p, q);
                p.setConstraint(q, pq.and(qp));
            }
        }
    }

    public void makeGroups() throws PluginConflictException {
        normalizeIndividualConstraints();
        /* Group plugins basing on WITH constraints; check for conflicts */
        List<Plugin> pending = new LinkedList<Plugin>(plugins.values());
        while (! pending.isEmpty()) {
            PluginGroup g = new PluginGroup(this, pending.remove(0));
            addGroup(g);
            pending.removeAll(g.getAll());
        }
    }

}
