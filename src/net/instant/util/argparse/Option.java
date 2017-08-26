package net.instant.util.argparse;

public abstract class Option<X> {

    private final String name;
    private final String help;

    public Option(String name, String help) {
        this.name = name;
        this.help = help;
    }

    public String getName() {
        return name;
    }

    public String getHelp() {
        return help;
    }

}
