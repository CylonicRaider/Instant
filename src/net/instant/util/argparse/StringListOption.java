package net.instant.util.argparse;

public class StringListOption extends ListOption<String> {

    public StringListOption(String name, Character shortname, String help) {
        super(name, shortname, help);
    }

    protected String getDefaultItemPlaceholder() {
        return "str";
    }

    protected String parseItem(String data) {
        return data;
    }

}
