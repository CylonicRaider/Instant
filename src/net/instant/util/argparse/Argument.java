package net.instant.util.argparse;

public abstract class Argument<X> extends BaseOption<X> {

    public Argument(String name, String help) {
        super(name, null, help);
    }

    public boolean isPositional() {
        return true;
    }

    public String formatName() {
        return '<' + getName() + '>';
    }
    public String formatUsage() {
        String name = formatName();
        StringBuilder sb = new StringBuilder();
        if (! isRequired()) sb.append('[');
        if (name != null) sb.append(name);
        if (! isRequired()) sb.append(']');
        return sb.toString();
    }

    public OptionValue<X> processOmitted(ArgumentParser p)
            throws ParseException {
        if (isRequired())
            throw new ParseException("Missing required argument <" +
                getName() + ">");
        return null;
    }

}
