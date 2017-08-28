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

    public abstract OptionValue<X> process(ArgumentParser p, ArgumentValue v,
        ArgumentSplitter s) throws ParseException;

}
