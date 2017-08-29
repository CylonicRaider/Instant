package net.instant.util.argparse;

public abstract class BaseOption<X> {

    private String name;
    private Character shortname;
    private String help;
    private boolean required;

    public BaseOption(String name, Character shortname, String help) {
        this.name = name;
        this.shortname = shortname;
        this.help = help;
    }

    public String getName() {
        return name;
    }
    public void setName(String n) {
        name = n;
    }
    public BaseOption<X> name(String n) {
        name = n;
        return this;
    }

    public Character getShortName() {
        return shortname;
    }
    public void setShortName(Character n) {
        shortname = n;
    }
    public BaseOption<X> shortname(Character n) {
        shortname = n;
        return this;
    }

    public String getHelp() {
        return help;
    }
    public void setHelp(String h) {
        help = h;
    }
    public BaseOption<X> help(String h) {
        help = h;
        return this;
    }

    public boolean isRequired() {
        return required;
    }
    public void setRequired(boolean r) {
        required = r;
    }
    public BaseOption<X> required() {
        required = true;
        return this;
    }

    public abstract boolean isPositional();

    public String formatUsage() {
        String name = formatName();
        String args = formatArguments();
        StringBuilder sb = new StringBuilder();
        if (! isRequired()) sb.append('[');
        if (name != null) sb.append(name);
        if (name != null && args != null) sb.append(' ');
        if (args != null) sb.append(args);
        if (! isRequired()) sb.append(']');
        return sb.toString();
    }
    public abstract String formatName();
    public abstract String formatArguments();
    public String formatHelp() {
        return getHelp();
    }

    public abstract OptionValue<X> process(ArgumentParser p, ArgumentValue v,
        ArgumentSplitter s) throws ParseException;
    public OptionValue<X> processOmitted(ArgumentParser p)
            throws ParseException {
        if (isRequired())
            throw new ParseException((isPositional()) ?
                "Missing required argument <" + getName() + ">" :
                "Missing required option --" + getName());
        return null;
    }

}
