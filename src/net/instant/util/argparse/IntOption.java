package net.instant.util.argparse;

public class IntOption extends ValueOption<Integer> {

    public IntOption(String name, Character shortname, String help) {
        super(name, shortname, help);
    }

    protected String getDefaultPlaceholder() {
        return "int";
    }

    protected Integer parse(String data) throws ParseException {
        try {
            return Integer.parseInt(data);
        } catch (NumberFormatException exc) {
            throw new ParseException("Invalid integer: " + data, exc);
        }
    }

}
