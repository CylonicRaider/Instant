package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArgumentParser {

    private final Map<String, Option<?>> options;
    private final Map<Character, Option<?>> shortOptions;
    private final List<Option<?>> arguments;
    private String progname;

    public ArgumentParser(String progname) {
        this.progname = progname;
        this.options = new LinkedHashMap<String, Option<?>>();
        this.shortOptions = new LinkedHashMap<Character, Option<?>>();
        this.arguments = new ArrayList<Option<?>>();
    }

    public String getProgName() {
        return progname;
    }
    public void setProgName(String name) {
        progname = name;
    }

    public List<Option<?>> getOptions() {
        return new ArrayList<Option<?>>(options.values());
    }

    public List<Option<?>> getArguments() {
        return new ArrayList<Option<?>>(arguments);
    }

    public <X> Option<X> addOption(Option<X> opt) {
        options.put(opt.getName(), opt);
        if (opt.getShortName() != null)
            shortOptions.put(opt.getShortName(), opt);
        return opt;
    }
    public boolean removeOption(Option<?> opt) {
        shortOptions.remove(opt.getShortName());
        return (options.remove(opt.getName()) != null);
    }

    public <X> Option<X> addArgument(Option<X> arg) {
        arguments.add(arg);
        return arg;
    }
    public boolean removeArgument(Option<?> arg) {
        return arguments.remove(arg);
    }

    public Option<?> getOption(ArgumentValue val) {
        switch (val.getType()) {
            case LONG_OPTION:
                return options.get(val.getValue());
            case SHORT_OPTION:
                return shortOptions.get(val.getValue().charAt(0));
            default:
                throw new IllegalArgumentException("Trying to resolve " +
                    "non-option");
        }
    }

    public ParseResult parse(String[] args) throws ParseException {
        Set<Option<?>> missing = new HashSet<Option<?>>(options.values());
        Iterator<Option<?>> argiter = arguments.iterator();
        ArgumentSplitter splitter = new ArgumentSplitter(
            Arrays.asList(args));
        List<OptionValue<?>> results = new LinkedList<OptionValue<?>>();
        boolean argsOnly = false;
        Option<?> opt;
        for (;;) {
            ArgumentValue v = splitter.next((argsOnly) ?
                ArgumentSplitter.Mode.FORCE_ARGUMENTS :
                ArgumentSplitter.Mode.OPTIONS);
            if (v == null) break;
            switch (v.getType()) {
                case LONG_OPTION: case SHORT_OPTION:
                    opt = getOption(v);
                    missing.remove(opt);
                    results.add(opt.process(this, v, splitter));
                    break;
                case VALUE:
                    throw new ParseException("Superfluous option value: " +
                        v.getValue());
                case ARGUMENT:
                    if (v.getValue().equals("--")) {
                        argsOnly = true;
                        continue;
                    } else if (! argiter.hasNext()) {
                        throw new ParseException("Superfluous argument: " +
                            v.getValue());
                    }
                    opt = argiter.next();
                    results.add(opt.process(this, v, splitter));
                    break;
                default:
                    throw new RuntimeException("Unknown ArgumentValue " +
                        "type?!");
            }
        }
        for (Option<?> o : missing) {
            if (o.isRequired())
                throw new ParseException("Missing required option --" +
                                         o.getName());
        }
        while (argiter.hasNext()) {
            Option<?> o = argiter.next();
            if (o.isRequired())
                throw new ParseException("Missing required option --" +
                                         o.getName());
        }
        return new ParseResult(results);
    }

}
