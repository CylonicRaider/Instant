package net.instant.plugins;

import java.io.IOException;
import java.util.ArrayList;
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
    public boolean hasGroup(PluginGroup g) {
        return groups.contains(g);
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

    protected boolean constraintOK(PluginGroup base,
            Iterable<PluginGroup> others, Constraint constraint) {
        for (PluginGroup g : others) {
            if (! base.getConstraint(g).isCompatible(constraint))
                return false;
        }
        return true;
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
        /* Merge groups where possible */
        List<PluginGroup> grps = new ArrayList<PluginGroup>(groups);
        for (int i = 0; i < grps.size(); i++) {
            PluginGroup g = grps.get(i);
            Set<PluginGroup> gPrecs = g.getAllPrecedessors();
            Set<PluginGroup> gSuccs = g.getAllSuccessors();
            if (gPrecs.contains(g))
                throw new GroupConstraintLoopException(g, Constraint.AFTER);
            if (gSuccs.contains(g))
                throw new GroupConstraintLoopException(g, Constraint.BEFORE);
            for (int j = i + 1; j < grps.size(); j++) {
                PluginGroup h = grps.get(j);
                // Check basic constraints
                if (! g.getConstraint(h).isCompatible(Constraint.WITH))
                    continue;
                // Calculate precedessors/successors
                if (gPrecs == null) gPrecs = g.getAllPrecedessors();
                if (gSuccs == null) gSuccs = g.getAllSuccessors();
                Set<PluginGroup> hPrecs = h.getAllPrecedessors();
                Set<PluginGroup> hSuccs = h.getAllSuccessors();
                // Strong constraints present, obviously, an obstacle.
                if (gPrecs.contains(h) || gSuccs.contains(h) ||
                    hPrecs.contains(g) || hSuccs.contains(g)) continue;
                // Weak constraints of precedessors or successors as well.
                if (! constraintOK(h, gPrecs, Constraint.AFTER) ||
                    ! constraintOK(h, gSuccs, Constraint.BEFORE) ||
                    ! constraintOK(g, hPrecs, Constraint.AFTER) ||
                    ! constraintOK(g, hSuccs, Constraint.BEFORE)) continue;
                // Can merge.
                g.merge(h);
                // Prepare for next iteration.
                grps.remove(j--);
                gPrecs = null;
                gSuccs = null;
            }
        }
    }

}
