package net.instant.plugins;

import java.net.URL;
import java.net.URLClassLoader;

public class PluginClassLoader extends URLClassLoader {

    public PluginClassLoader() {
        super(new URL[0]);
    }

    // Re-exporting as public.
    public void addURL(URL url) {
        super.addURL(url);
    }

}
