package net.instant.plugins;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class PluginGroup implements Comparable<PluginGroup> {

    private final PluginManager parent;
    private final SortedSet<Plugin> plugins;
    private final Map<Plugin, Constraint> constraints;
    private Set<PluginGroup> precedessors, successors;

    public PluginGroup(PluginManager m) {
        parent = m;
        plugins = new TreeSet<Plugin>();
        constraints = new HashMap<Plugin, Constraint>();
        precedessors = null;
        successors = null;
    }
    public PluginGroup(PluginManager parent, Plugin base)
            throws PluginConflictException {
        this(parent);
        add(base);
    }

    public int compareTo(PluginGroup g) {
        return Integer.compare(getIndex(), g.getIndex());
    }

    public SortedSet<Plugin> getAll() {
        return Collections.unmodifiableSortedSet(plugins);
    }
    public Map<Plugin, Constraint> getConstraints() {
        return Collections.unmodifiableMap(constraints);
    }

    public Constraint getConstraint(PluginGroup g) {
        Constraint ret = Constraint.INDIFFERENT_OF;
        for (Plugin p : g.getAll()) {
            Constraint c = constraints.get(p);
            if (c != null) ret = ret.and(c);
        }
        return ret;
    }

    public void add(Plugin p) throws PluginConflictException {
        Constraint pc = constraints.remove(p);
        if (pc != null && ! Constraint.WITH.isCompatible(pc))
            throw new PluginGroupConflictException(this, p, pc);
        Set<Plugin> adds = new LinkedHashSet<Plugin>();
        Map<Plugin, Constraint> cm = p.getConstraints();
        for (Map.Entry<Plugin, Constraint> e : cm.entrySet()) {
            Constraint oc = constraints.get(e.getKey()), nc;
            if (oc == null) {
                nc = e.getValue();
            } else if (oc.isCompatible(e.getValue())) {
                nc = oc.and(e.getValue());
            } else {
                throw new TernaryPluginConflictException(this, p, e.getKey(),
                                                         oc, e.getValue());
            }
            if (nc == Constraint.WITH) {
                adds.add(e.getKey());
            } else {
                constraints.put(e.getKey(), nc);
            }
        }
        if (! plugins.add(p)) return;
        p.setGroup(this);
        for (Plugin pl : adds) {
            PluginGroup gr = pl.getGroup();
            if (gr == null) {
                add(pl);
            } else {
                merge(gr);
            }
        }
        resetPrecSucc();
    }

    public void merge(PluginGroup other) throws PluginConflictException {
        for (Plugin p : other.getAll()) add(p);
        parent.removeGroup(other);
    }

    protected void resetPrecSucc() {
        if (precedessors != null) {
            for (PluginGroup g : precedessors)
                g.successors = null;
            precedessors = null;
        }
        if (successors != null) {
            for (PluginGroup g : successors)
                g.precedessors = null;
            successors = null;
        }
    }

    public Set<PluginGroup> getPrecedessors() {
        if (precedessors == null) {
            precedessors = new HashSet<PluginGroup>();
            for (Plugin p : plugins) {
                for (Plugin d : p.getPrecedessors())
                    precedessors.add(d.getGroup());
            }
        }
        return precedessors;
    }
    public Set<PluginGroup> getSuccessors() {
        if (successors == null) {
            successors = new HashSet<PluginGroup>();
            for (Plugin p : plugins) {
                for (Plugin d : p.getSuccessors())
                    successors.add(d.getGroup());
            }
        }
        return successors;
    }

    public Set<PluginGroup> getAllPrecedessors() {
        Set<PluginGroup> ret = new HashSet<PluginGroup>();
        getAllPrecedessors(ret);
        return ret;
    }
    public Set<PluginGroup> getAllSuccessors() {
        Set<PluginGroup> ret = new HashSet<PluginGroup>();
        getAllSuccessors(ret);
        return ret;
    }
    protected void getAllPrecedessors(Set<PluginGroup> drain) {
        for (PluginGroup g : getPrecedessors()) {
            if (! drain.add(g)) continue;
            g.getAllPrecedessors(drain);
        }
    }
    protected void getAllSuccessors(Set<PluginGroup> drain) {
        for (PluginGroup g : getSuccessors()) {
            if (! drain.add(g)) continue;
            g.getAllSuccessors(drain);
        }
    }

    public int getIndex() {
        Plugin p = plugins.first();
        if (p == null)
            throw new IllegalStateException("Comparing empty PluginGroup");
        return p.getIndex();
    }

    public String getNames() {
        StringBuilder sb = new StringBuilder("group(");
        boolean first = true;
        for (Plugin p : plugins) {
            if (first) {
                first = false;
            } else {
                sb.append(',');
            }
            sb.append(p.getName());
        }
        sb.append(')');
        return sb.toString();
    }

    public void addURLs() {
        for (Plugin p : plugins)
            p.addURL();
    }
    public void initializePlugins() throws BadPluginException {
        for (Plugin p : plugins) {
            Class<?> cls = p.fetchClass();
            p.initialize(cls);
        }
    }
    public void load() throws BadPluginException {
        addURLs();
        initializePlugins();
    }

}
