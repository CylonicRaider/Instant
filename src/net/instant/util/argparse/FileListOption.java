package net.instant.util.argparse;

import java.io.File;
import java.util.regex.Pattern;

public class FileListOption extends ListOption<File> {

    public FileListOption(String name, Character shortname, String help) {
        super(name, shortname, help);
        setSeparator(Pattern.quote(File.pathSeparator));
    }

    protected String getItemPlaceholder() {
        return "path";
    }

    protected File parseItem(String data) {
        return new File(data);
    }

}
