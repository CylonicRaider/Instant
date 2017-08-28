package net.instant.util.argparse;

public class StringOption extends ValueOption<String> {

    public StringOption(String name, Character shortname, String help) {
        super(name, shortname, help);
    }

    protected String getDefaultPlaceholder() {
        return "str";
    }

    protected String parse(String data) {
        return data;
    }

}
