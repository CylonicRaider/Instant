package net.instant.plugins;

import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class Plugin {

    private final String name;
    private final File path;
    private final JarFile file;
    private final String mainClass;
    private final PluginAttributes attrs;

    public Plugin(String name, File path, JarFile file)
            throws BadPluginException, IOException {
        this.name = name;
        this.path = path;
        this.file = file;
        Manifest mf = file.getManifest();
        if (mf == null)
            throw new BadPluginException("Plugin " + name +
                " has no manifest");
        this.mainClass = mf.getMainAttributes().getValue(
            Attributes.Name.MAIN_CLASS);
        this.attrs = new PluginAttributes(
            mf.getAttributes("Instant-Plugin"));
    }
    public Plugin(String name, File path) throws BadPluginException,
            IOException {
        this(name, path, new JarFile(path));
    }

    public String getName() {
        return name;
    }

    public File getPath() {
        return path;
    }

    public JarFile getFile() {
        return file;
    }

    public PluginAttributes getAttributes() {
        return attrs;
    }

}
