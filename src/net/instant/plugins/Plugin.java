package net.instant.plugins;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import net.instant.api.API1;
import net.instant.api.PluginData;
import net.instant.util.Util;

public class Plugin implements PluginData {

    // Plugins required to be present, and this must be after them.
    public static final PluginAttribute<Set<String>> DEPENDS =
        new StringSetAttribute("Depends");
    // Plugins required to be present (may be mutual).
    public static final PluginAttribute<Set<String>> REQUIRES =
        new StringSetAttribute("Requires");
    // This must be before those plugins (if present).
    public static final PluginAttribute<Set<String>> BEFORE =
        new StringSetAttribute("Before");
    // This must be after those plugins (if present).
    public static final PluginAttribute<Set<String>> AFTER =
        new StringSetAttribute("After");
    // Plugins that must not be present if this is.
    public static final PluginAttribute<Set<String>> BREAKS =
        new StringSetAttribute("Breaks");

    private final PluginManager parent;
    private final String name;
    private final File source;
    private final JarFile file;
    private final PluginAttributes attrs;
    private final String mainClass;
    private Set<String> dependencies;
    private boolean loaded;
    private Object data;

    public Plugin(PluginManager parent, String name, File source, JarFile file)
            throws BadPluginException, IOException {
        this.parent = parent;
        this.name = name;
        this.source = source;
        this.file = file;
        Manifest mf = file.getManifest();
        if (mf == null)
            throw new BadPluginException("Plugin " + name +
                " has no manifest");
        this.attrs = new PluginAttributes(mf.getAttributes("Instant-Plugin"),
            mf.getMainAttributes());
        this.mainClass = this.attrs.getRaw(Attributes.Name.MAIN_CLASS);
        this.dependencies = null;
        this.loaded = false;
        this.data = null;
    }
    public Plugin(PluginManager parent, String name, File source)
            throws BadPluginException, IOException {
        this(parent, name, source, new JarFile(source));
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

    public PluginAttributes getAttributes() {
        return attrs;
    }
    public <T> T getAttribute(PluginAttribute<T> attr) {
        return getAttributes().get(attr);
    }
    public String getAttribute(String name) {
        return attrs.getRaw(name);
    }

    public String getMainClassName() {
        return mainClass;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public Object getData() {
        return data;
    }

    public Iterable<String> getRequirements() {
        return Util.concat(getAttribute(REQUIRES), getAttribute(DEPENDS));
    }
    public Set<String> getDependencies() {
        if (dependencies == null) {
            Set<String> ret = new LinkedHashSet<String>();
            for (String n : getRequirements()) ret.add(n);
            dependencies = Collections.unmodifiableSet(ret);
        }
        return dependencies;
    }

    public void load() {
        try {
            parent.getClassLoader().addURL(source.toURI().toURL());
        } catch (MalformedURLException exc) {
            // *Should* not happen...
            throw new RuntimeException(exc);
        }
    }
    public Object init() throws PluginException {
        if (loaded) return data;
        try {
            Class<?> cls;
            if (mainClass == null) {
                cls = DefaultPlugin.class;
            } else {
                cls = Class.forName(mainClass, true,
                                    parent.getClassLoader());
            }
            Method init = cls.getMethod("initInstantPlugin1", API1.class,
                                        PluginData.class);
            if (! Modifier.isStatic(init.getModifiers()))
                throw new BadPluginException("Initializer method of " +
                    "plugin " + getName() + " is not static");
            data = init.invoke(null, parent.getAPI(), this);
            loaded = true;
            return data;
        } catch (ClassNotFoundException exc) {
            throw new BadPluginException("Plugin main class missing", exc);
        } catch (NoSuchMethodException exc) {
            throw new BadPluginException("Plugin initializer method missing",
                                         exc);
        } catch (IllegalAccessException exc) {
            throw new BadPluginException("Plugin initializer method is " +
                "not public", exc);
        } catch (InvocationTargetException exc) {
            // One could also rethrow the cause, at the loss of some
            // transparency.
            throw new RuntimeException(exc);
        }
    }

}
