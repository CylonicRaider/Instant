package net.instant.util.argparse;

public class StringOption extends Option<String> {

    public StringOption(String name, boolean positional,
                        String defaultValue) {
        super(name, 1, positional, false, defaultValue);
    }
    public StringOption(String name, boolean positional) {
        super(name, 1, positional, true, null);
    }

    public OptionValue<String> parse(OptionValue<String> old, String[] args) {
        return wrap(args[0]);
    }

}
