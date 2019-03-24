package net.instant.util.argparse;

import java.util.Arrays;

public class ArgumentParser {

    private OptionDispatcher dispatcher;
    private String version;

    public ArgumentParser(String progname, String version,
                          String description) {
        this.dispatcher = new OptionDispatcher(progname, description);
        this.version = version;
    }

    public OptionDispatcher getDispatcher() {
        return dispatcher;
    }
    public void setDispatcher(OptionDispatcher disp) {
        dispatcher = disp;
    }

    public String getProgName() {
        return getDispatcher().getName();
    }
    public void setProgName(String name) {
        getDispatcher().setName(name);
    }

    public String getVersion() {
        return version;
    }
    public void setVersion(String ver) {
        version = ver;
    }

    public String getDescription() {
        return getDispatcher().getDescription();
    }
    public void setDescription(String desc) {
        getDispatcher().setDescription(desc);
    }

    public <X extends Processor> X add(X arg) {
        if (arg instanceof Option<?>) {
            getDispatcher().addOption((Option<?>) arg);
        } else {
            getDispatcher().addArgument(arg);
        }
        return arg;
    }
    public void remove(Processor opt) {
        getDispatcher().remove(opt);
    }

    public void addStandardOptions() {
        add(HelpAction.makeOption(this));
        if (version != null) add(VersionAction.makeOption(this));
    }

    public ParseResult parse(String[] args) throws ParsingException {
        return parse(Arrays.asList(args));
    }
    public ParseResult parse(Iterable<String> args) throws ParsingException {
        ArgumentSplitter splitter = new ArgumentSplitter(args);
        ParseResultBuilder result = new ParseResultBuilder();
        getDispatcher().startParsing(result);
        getDispatcher().parse(splitter, result);
        ArgumentSplitter.ArgValue probe = splitter.peek(ArgumentSplitter.Mode.OPTIONS);
        if (probe != null)
            throw new ParsingException("Superfluous " + probe);
        getDispatcher().finishParsing(result);
        return result;
    }

}
