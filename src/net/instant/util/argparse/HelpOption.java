package net.instant.util.argparse;

import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;

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

    private static List<BaseOption<?>> sortedOptions(ArgumentParser p) {
        List<BaseOption<?>> ret = new LinkedList<BaseOption<?>>();
        p.getOptions(ret, false);
        p.getOptions(ret, true);
        return ret;
    }

    private static String makeWidth(int w) {
        return (w == 0) ? "" : Integer.toString(-w);
    }

    public static String formatUsage(ArgumentParser p, boolean prefix) {
        StringBuilder sb = new StringBuilder();
        if (prefix) {
            sb.append("USAGE: ");
            if (p.getProgName() == null) {
                sb.append("...");
            } else {
                sb.append(p.getProgName());
            }
        }
        for (BaseOption<?> opt : sortedOptions(p)) {
            if (sb.length() != 0) sb.append(' ');
            sb.append(opt.formatUsage());
        }
        return sb.toString();
    }
    public static String formatDescription(ArgumentParser p) {
        return p.getDescription();
    }
    public static String formatHelp(ArgumentParser p) {
        StringBuilder sb = new StringBuilder();
        List<HelpLine> lines = new LinkedList<HelpLine>();
        for (BaseOption<?> opt : sortedOptions(p)) {
            lines.add(new HelpLine(opt.formatName(),
                ((opt.isPositional()) ? ":" : ""), opt.formatArguments(),
                opt.formatHelp()));
        }
        HelpLine.format(lines, new Formatter(sb, null));
        return sb.toString();
    }

    public static String formatFullHelp(ArgumentParser p) {
        String usage = formatUsage(p, true), desc = formatDescription(p);
        String help = formatHelp(p);
        StringBuilder sb = new StringBuilder(usage);
        if (desc != null) sb.append('\n').append(desc);
        return sb.append('\n').append(help).toString();
    }

    public static void displayHelp(ArgumentParser p, int code) {
        System.err.println(formatFullHelp(p));
        System.exit(code);
    }

}
