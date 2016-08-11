package net.instant.util.argparse;

public class IntegerOption extends Option<Integer> {

    public IntegerOption(String name, boolean positional,
                         Integer defaultValue) {
        super(name, 1, positional, false, defaultValue);
    }
    public IntegerOption(String name, boolean positional) {
        super(name, 1, positional, true, null);
    }

    public String getPlaceholder(int index) {
        return "int";
    }
    public OptionValue<Integer> parse(OptionValue<Integer> old,
                                      String[] args) throws ParseException {
        try {
            return wrap(Integer.parseInt(args[0]));
        } catch (NumberFormatException exc) {
            throw new ParseException("Bad value for option '" + getName() +
                "': " + args[0], exc);
        }
    }

}
