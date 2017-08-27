package net.instant.util.argparse;

public abstract class Option<X> {

    private String name;
    private char shortname;
    private String help;

    public Option(String name, char shortname, String help) {
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

    public char getShortName() {
        return shortname;
    }
    public void setShortName(char n) {
        shortname = n;
    }
    public Option<X> shortname(char n) {
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

    public abstract OptionValue<X> process(ArgumentValue v,
        ArgumentSplitter s) throws ParseException;

}
