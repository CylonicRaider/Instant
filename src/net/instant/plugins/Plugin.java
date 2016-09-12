package net.instant.plugins;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class Plugin implements Comparable<Plugin> {

    public static final PluginAttribute<Set<String>> DEPENDS =
        new StringSetAttribute("Depends");

    public static final PluginConstraintAttribute BEFORE =
        new PluginConstraintAttribute("Before", Constraint.BEFORE);
    public static final PluginConstraintAttribute NOT_AFTER =
        new PluginConstraintAttribute("Not-After", Constraint.NOT_AFTER);
    public static final PluginConstraintAttribute WITH =
        new PluginConstraintAttribute("With", Constraint.WITH);
    public static final PluginConstraintAttribute NOT_BEFORE =
        new PluginConstraintAttribute("Not-Before", Constraint.NOT_BEFORE);
    public static final PluginConstraintAttribute AFTER =
        new PluginConstraintAttribute("After", Constraint.AFTER);
    public static final PluginConstraintAttribute BREAKS =
        new PluginConstraintAttribute("Breaks", Constraint.BREAKS);

    private static final PluginConstraintAttribute[] conAttrs =
        new PluginConstraintAttribute[] {
            BEFORE, NOT_AFTER, WITH, NOT_BEFORE, AFTER, BREAKS
        };

    private final PluginManager parent;
    private final String name;
    private final File source;
    private final JarFile file;
    private final Manifest manifest;
    private final Attributes rawAttrs;
    private final Map<PluginAttribute<?>, Object> attrs;
    private final Map<Plugin, Constraint> constraints;
    private PluginGroup group;
    private Object pluginData;

    public Plugin(PluginManager parent, String name, File source)
            throws BadPluginException, IOException {
        this.parent = parent;
        this.name = name;
        this.source = source;
        this.file = new JarFile(source);
        this.manifest = file.getManifest();
        if (this.manifest == null)
            throw new BadPluginException("Plugin missing manifest");
        this.rawAttrs = manifest.getAttributes("Instant-Plugin");
        this.attrs = new HashMap<PluginAttribute<?>, Object>();
        this.constraints = new HashMap<Plugin, Constraint>();
        this.group = null;
        this.pluginData = null;
    }

    public int compareTo(Plugin other) {
        return Integer.compare(getIndex(), other.getIndex());
    }

    public PluginManager getParent() {
        return parent;
    }
    public String getName() {
        return name;
    }
    public File getSource() {
        return source;
    }
    public JarFile getFile() {
        return file;
    }
    public Manifest getManifest() {
        return manifest;
    }
    public Attributes getRawAttributes() {
        return rawAttrs;
    }

    public <T> T get(PluginAttribute<T> attr) {
        @SuppressWarnings("unchecked")
        T val = (T) attrs.get(attr);
        if (val == null) {
            Attributes r = getRawAttributes();
            String v = (r == null) ? null : r.getValue(attr.getName());
            val = attr.parse(v);
            attrs.put(attr, (Object) val);
        }
        return val;
    }

    public Set<String> getDependencies() {
        Set<String> deps = get(DEPENDS);
        if (deps == null) deps = Collections.emptySet();
        return deps;
    }

    public Constraint getConstraint(Plugin other) {
        Constraint ret = constraints.get(other);
        if (ret == null) {
            String name = other.getName();
            ret = Constraint.INDIFFERENT_OF;
            for (PluginConstraintAttribute a : conAttrs) {
                Set<String> cons = get(a);
                if (cons != null && cons.contains(name))
                    ret = ret.and(a.getConstraint());
            }
            constraints.put(other, ret);
        }
        return ret;
    }
    public void setConstraint(Plugin other, Constraint c) {
        constraints.put(other, c);
    }
    public Map<Plugin, Constraint> getConstraints() {
        return Collections.unmodifiableMap(constraints);
    }

    protected Set<Plugin> matchConstraint(Constraint c) {
        Set<Plugin> ret = new HashSet<Plugin>();
        for (Map.Entry<Plugin, Constraint> ent : constraints.entrySet()) {
            if (ent.getValue() == c) ret.add(ent.getKey());
        }
        return ret;
    }
    public Set<Plugin> getPrecedessors() {
        return matchConstraint(Constraint.AFTER);
    }
    public Set<Plugin> getSuccessors() {
        return matchConstraint(Constraint.BEFORE);
    }

    public PluginGroup getGroup() {
        return group;
    }
    public void setGroup(PluginGroup g) {
        group = g;
    }

    public Object getData() {
        return pluginData;
    }
    public void setData(Object data) {
        pluginData = data;
    }

    public int getIndex() {
        return parent.getIndex(this);
    }

}
