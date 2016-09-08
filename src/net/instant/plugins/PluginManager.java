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

    protected void normalizeIndividualConstraints()
            throws PluginConflictException {
        /* Ensure that all inter-plugin constraints are mutual and that no
         * plugins break each other or have (temporal) dependency loops. */
        for (Plugin p : getAll()) {
            for (Plugin q : getAll()) {
                Constraint pq = p.getConstraint(q);
                Constraint qp = q.getConstraint(p);
                if (! pq.isCompatibleWith(qp))
                    throw new PluginConflictException(p, q);
                p.setConstraint(q, pq.and(qp));
            }
        }
    }

}
