package net.instant.plugins;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class PluginGroup {

    private final PluginManager parent;
    private final Set<Plugin> plugins;
    private final Map<Plugin, Constraint> constraints;
    private Set<PluginGroup> precedessors, successors;

    public PluginGroup(PluginManager m) {
        parent = m;
        plugins = new HashSet<Plugin>();
        constraints = new HashMap<Plugin, Constraint>();
        precedessors = null;
        successors = null;
    }
    public PluginGroup(PluginManager parent, Plugin base)
            throws PluginConflictException {
        this(parent);
        add(base);
    }

    public Set<Plugin> getAll() {
        return Collections.unmodifiableSet(plugins);
    }
    public Map<Plugin, Constraint> getConstraints() {
        return Collections.unmodifiableMap(constraints);
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
            g.getAllPrecedessors(drain);
        }
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

}
