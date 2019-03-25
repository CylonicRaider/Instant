package net.instant.util.argparse;

import java.util.Arrays;
import java.util.Formatter;
import java.util.List;

public class ArgumentParser {

    public static class HelpAction extends RunnableAction {

        public static final String USAGE_LINE_HEADER = "USAGE: ";

        private final ArgumentParser parser;

        public HelpAction(ArgumentParser parser) {
            this.parser = parser;
        }

        public ArgumentParser getParser() {
            return parser;
        }

        public String formatParserName() {
            String pn = getParser().getProgName();
            return (pn == null) ? "..." : pn;
        }

        public String formatParserUsage() {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Processor p : getParser().getDispatcher().getAllOptions()) {
                String part = p.formatUsage();
                if (part == null) {
                    continue;
                } else if (first) {
                    first = false;
                } else {
                    sb.append(" ");
                }
                sb.append(part);
            }
            return (first) ? null : sb.toString();
        }

        public String formatParserDescription() {
            return getParser().getDescription();
        }

        public List<HelpLine> getAllParserHelpLines() {
            return getParser().getDispatcher().getAllHelpLines();
        }

        public String formatUsageLine() {
            String usage = formatParserUsage();
            String ret = USAGE_LINE_HEADER + formatParserName();
            if (usage != null) ret += " " + usage;
            return ret;
        }

        public String formatHelp() {
            StringBuilder sb = new StringBuilder();
            HelpLine.format(getAllParserHelpLines(), new Formatter(sb, null));
            return sb.toString();
        }

        public String formatFullHelp() {
            StringBuilder sb = new StringBuilder(formatUsageLine());
            String desc = formatParserDescription();
            if (desc != null) sb.append('\n').append(desc);
            return sb.append('\n').append(formatHelp()).toString();
        }

        public void run() {
            System.err.println(formatFullHelp());
            System.exit(0);
        }

        public static Processor makeOption(ArgumentParser parser) {
            return new Option<HelpAction>("help", '?', "Display help.",
                new HelpAction(parser));
        }

    }

    public static class VersionAction extends RunnableAction {

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
            return new Option<VersionAction>("version", 'V',
                "Display version.", new VersionAction(parser));
        }

    }

    private OptionDispatcher dispatcher;
    private String version;
    private String description;

    public ArgumentParser(String progname, String version,
                          String description) {
        this.dispatcher = new OptionDispatcher(progname);
        this.version = version;
        this.description = description;
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
        return description;
    }
    public void setDescription(String desc) {
        description = desc;
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
        ArgumentSplitter.ArgValue probe = splitter.peek(
            ArgumentSplitter.Mode.OPTIONS);
        if (probe != null)
            throw new ParsingException("Superfluous " + probe);
        getDispatcher().finishParsing(result);
        return result;
    }

}
