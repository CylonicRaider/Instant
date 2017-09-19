package net.instant.util.argparse;

public class VersionOption extends ActionOption {

    public VersionOption(String name, Character shortName, String help) {
        super(name, shortName, help);
    }
    public VersionOption() {
        this("version", 'V', "Display version");
    }

    protected void run(ArgumentParser p) {
        displayVersion(p, 0);
    }

    public static String formatVersion(ArgumentParser p) {
        return p.getProgName() + " " +
            ((p.getVersion() == null) ? "???" : p.getVersion());
    }

    public static void displayVersion(ArgumentParser p, int code) {
        System.err.println(formatVersion(p));
        System.exit(code);
    }

}
