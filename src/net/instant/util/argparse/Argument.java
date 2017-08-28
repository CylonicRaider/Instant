package net.instant.util.argparse;

public abstract class Argument<X> extends BaseOption<X> {

    public Argument(String name, Character shortname, String help) {
        super(name, shortname, help);
    }

    public boolean isPositional() {
        return true;
    }

}
