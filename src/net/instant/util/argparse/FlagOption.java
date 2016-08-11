package net.instant.util.argparse;

public class FlagOption extends Option<Boolean> {

    public FlagOption(String name) {
        super(name, 0, false, false, false);
    }

    public String getPlaceholder(int index) {
        return null;
    }
    public OptionValue<Boolean> parse(OptionValue<Boolean> old,
                                      String[] args) {
        return wrap(Boolean.TRUE);
    }

}
