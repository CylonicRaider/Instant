package net.instant.plugins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PluginFetcher {

    private final List<File> singlePath;
    private final List<File> dirPath;

    public PluginFetcher() {
        singlePath = new ArrayList<File>();
        dirPath = new ArrayList<File>();
    }

    public void addPath(File ent) {
        if (ent.isDirectory()) {
            dirPath.add(ent);
        } else if (ent.isFile()) {
            singlePath.add(ent);
        }
    }

    public File getPath(String name) {
        if (name.contains("/")) {
            File ret = new File(name);
            if (! ret.isFile()) return null;
            return ret;
        }
        for (File p : singlePath) if (matches(name, p)) return p;
        for (File p : dirPath) {
            for (File f : p.listFiles()) if (matches(name, f)) return f;
        }
        return null;
    }

    public Plugin fetch(PluginManager parent, String name)
            throws IOException, PluginException {
        File path = getPath(name);
        if (path == null) return null;
        if (name.contains("/")) name = getName(name);
        return new Plugin(parent, name, path);
    }

    public static String getName(String path) {
        String r = path.split("_", 2)[0];
        if (r.endsWith(".jar")) r = r.substring(0, r.length() - 4);
        return r;
    }
    public static String getName(File path) {
        return getName(path.getName());
    }
    public static boolean matches(String name, File path) {
        return (getName(path).equals(name) && path.isFile());
    }

}
