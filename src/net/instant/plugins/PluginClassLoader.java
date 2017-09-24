package net.instant.plugins;

import java.net.URL;
import java.net.URLClassLoader;

public class PluginClassLoader extends URLClassLoader {

    public PluginClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    // Re-exporting as public.
    public void addURL(URL url) {
        super.addURL(url);
    }

}
