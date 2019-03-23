package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

public class HelpAction extends RunnableAction implements MultiProcessor {

    public static final String USAGE_LINE_HEADER = "USAGE: ";

    private final ArgumentParser parser;

    public HelpAction(ArgumentParser parser) {
        this.parser = parser;
    }

    public ArgumentParser getParser() {
        return parser;
    }

    public String formatName() {
        String pn = getParser().getProgName();
        return (pn == null) ? "..." : pn;
    }

    public String formatUsage() {
        StringBuilder sb = new StringBuilder();
        boolean first = false;
        for (BaseOption<?> o : sortedOptions(getParser())) {
            String part = o.formatUsage();
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

    public String formatDescription() {
        return getParser().getDescription();
    }

    public List<HelpLine> getAllHelpLines() {
        List<HelpLine> ret = new ArrayList<HelpLine>();
        for (BaseOption<?> o : sortedOptions(getParser())) {
            if (o instanceof Processor) {
                OptionDispatcher.accumulateHelpLines((Processor) o, ret);
            } else {
                ret.add(o.getHelpLine());
            }
        }
        return ret;
    }

    public String formatUsageLine() {
        String usage = formatUsage();
        String ret = USAGE_LINE_HEADER + formatName();
        if (usage != null) ret += " " + usage;
        return ret;
    }

    public String formatHelp() {
        StringBuilder sb = new StringBuilder();
        HelpLine.format(getAllHelpLines(), new Formatter(sb, null));
        return sb.toString();
    }

    public String formatFullHelp() {
        StringBuilder sb = new StringBuilder(formatUsageLine());
        String desc = formatDescription();
        if (desc != null) sb.append('\n').append(desc);
        return sb.append('\n').append(formatHelp()).toString();
    }

    public void run() {
        System.err.println(formatFullHelp());
        System.exit(0);
    }

    static List<BaseOption<?>> sortedOptions(ArgumentParser p) {
        List<BaseOption<?>> ret = new ArrayList<BaseOption<?>>();
        p.getOptions(ret, false);
        p.getOptions(ret, true);
        return ret;
    }

    public static Processor makeOption(ArgumentParser parser) {
        return new ValueOption<Void>("help", '?', "Display help.",
                                     new HelpAction(parser));
    }

}
