package net.instant.util.argparse;

public abstract class Option<X> {

    private String name;
    private String help;

    public Option(String name, String help) {
        this.name = name;
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

}
