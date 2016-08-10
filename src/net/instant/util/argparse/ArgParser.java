package net.instant.util.argparse;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArgParser {

    private final Map<String, Option<?>> options;
    private final Set<Option<?>> arguments;

    public ArgParser() {
        options = new LinkedHashMap<String, Option<?>>();
        arguments = new LinkedHashSet<Option<?>>();
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

    public <T extends Option> T add(T opt) {
        if (opt.isPositional()) {
            arguments.add(opt);
        } else {
            options.put(opt.getName(), opt);
        }
        return opt;
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

}
