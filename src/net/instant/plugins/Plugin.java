package net.instant.plugins;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class Plugin {

    private final PluginManager parent;
    private final String name;
    private final File source;
    private final JarFile file;
    private final Manifest manifest;
    private Object pluginData;

    public Plugin(PluginManager parent, String name, File source)
            throws IOException {
        this.parent = parent;
        this.name = name;
        this.source = source;
        this.file = new JarFile(source);
        this.manifest = file.getManifest();
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

    public Object getData() {
        return pluginData;
    }
    public void setData(Object data) {
        pluginData = data;
    }

}
