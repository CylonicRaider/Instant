package net.instant.util.argparse;

import java.util.Arrays;
import java.util.List;

public class StringListOption extends ListOption<String> {

    public StringListOption(String name, boolean positional) {
        super(name, positional);
    }

    public String getSeparator() {
        return ",";
    }

    public String getItemPlaceholder() {
        return "str";
    }

    public List<String> parseItems(String[] raw) {
        return Arrays.asList(raw);
    }

}
