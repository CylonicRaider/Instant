package net.instant.util.argparse;

public class VersionAction extends RunnableAction {

    private final ArgumentParser parser;

    public VersionAction(ArgumentParser parser) {
        this.parser = parser;
    }

    public ArgumentParser getParser() {
        return parser;
    }

    public String formatVersionLine() {
        String ret = getParser().getProgName();
        String version = getParser().getVersion();
        if (version != null) ret += " " + version;
        return ret;
    }

    public void run() {
        System.err.println(formatVersionLine());
        System.exit(0);
    }

    public static Processor makeOption(ArgumentParser parser) {
        return new Option<Void>("version", 'V', "Display version.",
                                new VersionAction(parser));
    }

}
