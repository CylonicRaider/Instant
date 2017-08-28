package net.instant.util.argparse;

public abstract class Option<X> extends BaseOption<X> {

    public Option(String name, Character shortname, String help) {
        super(name, shortname, help);
    }

    public boolean isPositional() {
        return false;
    }

    public String formatName() {
        StringBuilder sb = new StringBuilder();
        sb.append("--");
        sb.append(getName());
        if (getShortName() != null) {
            sb.append("|-");
            sb.append(getShortName());
        }
        return sb.toString();
    }

}
