package net.instant.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import net.instant.api.API1;

public class PluginManager {

    private final Map<String, Plugin> plugins;
    private final Map<Plugin, Integer> order;
    private final Set<PluginGroup> groups;
    private final List<String> pendingFetches;
    private API1 apiImpl;
    private PluginClassLoader classLoader;
    private PluginFetcher fetcher;
    private int nextIndex;

    public PluginManager(API1 api) {
        plugins = new LinkedHashMap<String, Plugin>();
        order = new HashMap<Plugin, Integer>();
        groups = new HashSet<PluginGroup>();
        pendingFetches = new LinkedList<String>();
        apiImpl = api;
        classLoader = new PluginClassLoader(getClass().getClassLoader());
        fetcher = new PluginFetcher();
        nextIndex = 0;
    }

    public API1 getAPI() {
        return apiImpl;
    }
    public void setAPI(API1 api) {
        apiImpl = api;
    }

    public PluginClassLoader getClassLoader() {
        return classLoader;
    }
    public void setClassLoader(PluginClassLoader cl) {
        classLoader = cl;
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
        order.remove(plugins.get(p.getName()));
        plugins.put(p.getName(), p);
        order.put(p, nextIndex++);
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
    public void fetchAll(Iterable<String> l)
            throws IOException, PluginException {
        for (String n : l) fetch(n);
    }

    public int getIndex(Plugin p) {
        return order.get(p);
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

    public void queue(String name) {
        pendingFetches.add(name);
    }
    public void queueAll(Iterable<String> l) {
        for (String s : l) queue(s);
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

    protected void walkGroups(PluginGroup base, List<PluginGroup> drain,
            Set<PluginGroup> visited) throws GroupConstraintLoopException {
        if (! visited.add(base))
            throw new GroupConstraintLoopException(base, Constraint.AFTER);
        SortedSet<PluginGroup> precs =
            new TreeSet<PluginGroup>(base.getPrecedessors());
        for (PluginGroup p : precs)
            walkGroups(p, drain, visited);
        drain.add(base);
    }
    public List<PluginGroup> orderGroups() throws PluginConflictException {
        SortedSet<PluginGroup> g = new TreeSet<PluginGroup>(groups);
        List<PluginGroup> ret = new ArrayList<PluginGroup>();
        Set<PluginGroup> visited = new HashSet<PluginGroup>();
        int lastSize = 0;
        while (! g.isEmpty()) {
            walkGroups(g.first(), ret, visited);
            g.removeAll(ret.subList(lastSize, ret.size()));
            lastSize = ret.size();
        }
        Collections.reverse(ret);
        return ret;
    }

    public void load() throws IOException, PluginException {
        fetchAll(pendingFetches);
        pendingFetches.clear();
        for (PluginGroup g : orderGroups())
            g.load();
    }

}
