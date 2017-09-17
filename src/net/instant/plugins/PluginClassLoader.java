package net.instant.plugins;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;

public class PluginClassLoader extends URLClassLoader {

    private Set<URL> seen;

    public PluginClassLoader() {
        super(new URL[0]);
        seen = new HashSet<URL>();
    }

    public void addURL(URL url) {
        if (seen.add(url))
            super.addURL(url);
    }

}
