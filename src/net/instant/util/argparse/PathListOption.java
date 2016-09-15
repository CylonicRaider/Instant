package net.instant.util.argparse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PathListOption extends ListOption<File> {

    public PathListOption(String name, boolean positional) {
        super(name, positional);
    }

    public String getSeparator() {
        return File.pathSeparator;
    }

    public String getItemPlaceholder() {
        return "path";
    }

    public List<File> parseItems(String[] raw) {
        List<File> ret = new ArrayList<File>(raw.length);
        for (String path : raw) ret.add(new File(path));
        return ret;
    }

}
