package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArgParser {

    private final String appname;
    private final Map<String, Option<?>> options;
    private final Set<Option<?>> arguments;

    public ArgParser(String appname) {
        this.appname = appname;
        this.options = new LinkedHashMap<String, Option<?>>();
        this.arguments = new LinkedHashSet<Option<?>>();
    }

    public String getAppName() {
        return appname;
    }
    public Set<Option<?>> getOptions() {
        return Collections.unmodifiableSet(
            new LinkedHashSet<Option<?>>(options.values()));
    }
    public Set<Option<?>> getArguments() {
        return Collections.unmodifiableSet(arguments);
    }

    public Set<Option<?>> getAllOptions() {
        Set<Option<?>> ret = new LinkedHashSet<Option<?>>();
        ret.addAll(options.values());
        ret.addAll(arguments);
        return ret;
    }

    public <T extends Option<?>> T add(T opt) {
        opt.setParent(this);
        if (opt.isPositional()) {
            arguments.add(opt);
        } else {
            options.put(opt.getName(), opt);
        }
        return opt;
    }
    public <T extends Option<?>> T add(T opt, String help) {
        add(opt);
        opt.setHelp(help);
        return opt;
    }
    public ActionOption addHelp() {
        return add(new ActionOption("help") {
            public void run() {
                System.err.println(getParent().formatHelp());
                System.exit(0);
            }
        }, "Display help");
    }

    public ParseResult parse(String[] args) throws ParseException {
        final Map<Option<?>, OptionValue<?>> res =
            new LinkedHashMap<Option<?>, OptionValue<?>>();
        final Iterator<Option<?>> positional = arguments.iterator();
        Argument.walkGroups(args, new Argument.GroupVisitor() {

            private Option<?> opt;

            public int first(Argument arg) throws ParseException {
                if (arg.isOption()) {
                    opt = options.get(arg.getValue());
                    if (opt == null)
                        throw new ParseException("No such option: '" +
                            arg.getValue() + "'");
                } else if (positional.hasNext()) {
                    opt = positional.next();
                } else {
                    throw new ParseException("Too many arguments");
                }
                if (opt.isPositional()) {
                    return opt.getNumArguments() - 1;
                } else {
                    return opt.getNumArguments();
                }
            }

            public void done(List<Argument> group) throws ParseException {
                int sk = (opt.isPositional()) ? 0 : 1;
                String[] args = new String[group.size() - sk];
                for (int i = 0; i < args.length; i++)
                    args[i] = group.get(i + sk).getValue();
                @SuppressWarnings({"unchecked", "rawtypes"})
                OptionValue v = opt.parse((OptionValue) res.get(opt), args);
                if (v != null) res.put(opt, v);
                opt = null;
            }

            public void end(List<Argument> remaining) throws ParseException {
                if (! remaining.isEmpty())
                    throw new ParseException("Too few arguments for " +
                        "option '" + opt.getName() + "'");
            }

        });
        for (Option<?> o : getAllOptions()) {
            if (o.isRequired() && res.get(o) == null)
                throw new ParseException("Missing required parameter '" +
                    o.getName() + "'");
        }
        return new ParseResult(res.values());
    }

    private String mcv(String prefix, int length, String suffix) {
        if (length == 0) {
            return prefix.replaceAll("-$", "") + suffix;
        } else {
            return prefix + length + suffix;
        }
    }

    public String formatUsage() {
        StringBuilder sb = new StringBuilder();
        sb.append("USAGE: ");
        sb.append(getAppName());
        for (Option<?> o : getAllOptions()) {
            sb.append(' ');
            sb.append(o.formatUsage());
        }
        return sb.toString();
    }
    public String formatHelp() {
        StringBuilder sb = new StringBuilder(formatUsage());
        int colL = 0, colC = 0, colP = 0;
        List<String[]> rows = new ArrayList<String[]>();
        for (Option<?> o : getAllOptions()) {
            String[] row = o.formatHelp();
            rows.add(row);
            colL = Math.max(colL, row[0].length() + row[1].length());
            colC = Math.max(colC, row[1].length());
            colP = Math.max(colP, row[2].length());
        }
        String sp = (colP != 0) ? " " : "";
        String fmtL = "\n" + mcv("%-", colL, "s") + sp +
                      mcv("%-", colP, "s: %s");
        String fmtS = "\n" + mcv("%-", colL - colC, "s") +
                      mcv("%", colC, "s") + sp + mcv("%-", colP, "s: %s");
        for (String[] row : rows) {
            if (row[1].isEmpty()) {
                sb.append(String.format(fmtL, row[0], row[2], row[3]));
            } else {
                sb.append(String.format(fmtS, row[0], row[1], row[2],
                                        row[3]));
            }
        }
        return sb.toString();
    }

}
