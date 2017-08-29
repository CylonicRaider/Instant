package net.instant.util.argparse;

import java.util.LinkedList;
import java.util.List;

public class HelpOption extends ActionOption {

    public HelpOption(String name, Character shortName, String help) {
        super(name, shortName, help);
    }
    public HelpOption() {
        this("help", '?', "Display help.");
    }

    protected void run(ArgumentParser p) {
        displayHelp(p, 0);
    }

    private static List<BaseOption<?>> sortedOptions(ArgumentParser p) {
        List<BaseOption<?>> ret = new LinkedList<BaseOption<?>>();
        p.getOptions(ret, false);
        p.getOptions(ret, true);
        return ret;
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
            sb.append(' ');
            sb.append(opt.formatUsage());
        }
        return sb.toString();
    }
    public static String formatHelp(ArgumentParser p) {
        List<String[]> columns = new LinkedList<String[]>();
        int wdN = 0, wdA = 0;
        for (BaseOption<?> opt : sortedOptions(p)) {
            String[] item = new String[] {
                opt.formatName(), ((opt.isPositional()) ? ":" : ""),
                opt.formatArguments(), opt.formatHelp()
            };
            wdN = Math.max(wdN, item[0].length() + item[1].length());
            wdA = Math.max(wdA, item[2].length());
            columns.add(item);
        }
        String optFormat = String.format("%%-%ds%%s %%-%ds: %%s", wdN, wdA);
        String argFormat = String.format("%%-%ds%%s %%-%ds: %%s", wdN - 1,
                                         wdA);
        StringBuilder sb = new StringBuilder();
        for (String[] col : columns) {
            if (sb.length() != 0) sb.append('\n');
            // Varargs magic!
            sb.append(String.format(((col[1].isEmpty()) ? optFormat :
                argFormat), (Object[]) col));
        }
        return sb.toString();
    }

    public static void displayHelp(ArgumentParser p, int code) {
        System.err.println(formatUsage(p, true));
        System.err.println(formatHelp(p));
        System.exit(code);
    }

}
