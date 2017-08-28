package net.instant.util.argparse;

public abstract class Option<X> {

    private String name;
    private Character shortname;
    private String help;
    private boolean required;

    public Option(String name, Character shortname, String help) {
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
    public Option<X> name(String n) {
        name = n;
        return this;
    }

    public Character getShortName() {
        return shortname;
    }
    public void setShortName(Character n) {
        shortname = n;
    }
    public Option<X> shortname(Character n) {
        shortname = n;
        return this;
    }

    public String getHelp() {
        return help;
    }
    public void setHelp(String h) {
        help = h;
    }
    public Option<X> help(String h) {
        help = h;
        return this;
    }

    public boolean isRequired() {
        return required;
    }
    public void setRequired(boolean r) {
        required = r;
    }
    public Option<X> required() {
        required = true;
        return this;
    }

    public abstract OptionValue<X> process(ArgumentParser p, ArgumentValue v,
        ArgumentSplitter s) throws ParseException;

}
