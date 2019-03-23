package net.instant.util.argparse;

public class HelpOption extends ActionOption {

    public HelpOption(String name, Character shortName, String help) {
        super(name, shortName, help);
    }
    public HelpOption() {
        this("help", '?', "Display help");
    }

    public void run() {
        if (getParser() == null)
            throw new NullPointerException("Cannot display help " +
                                           "without parser");
        displayHelp(getParser(), 0);
    }

    private static String makeWidth(int w) {
        return (w == 0) ? "" : Integer.toString(-w);
    }

    public static String formatUsage(ArgumentParser p, boolean prefix) {
        if (prefix) {
            return new HelpAction(p).formatUsageLine();
        } else {
            return new HelpAction(p).formatUsage();
        }
    }
    public static String formatDescription(ArgumentParser p) {
        return new HelpAction(p).formatDescription();
    }
    public static String formatHelp(ArgumentParser p) {
        return new HelpAction(p).formatHelp();
    }

    public static String formatFullHelp(ArgumentParser p) {
        return new HelpAction(p).formatFullHelp();
    }

    public static void displayHelp(ArgumentParser p, int code) {
        System.err.println(formatFullHelp(p));
        System.exit(code);
    }

}
