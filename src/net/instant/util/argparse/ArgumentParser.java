package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArgumentParser {

    private final Map<String, BaseOption<?>> options;
    private final Map<Character, BaseOption<?>> shortOptions;
    private String progname;
    private String version;

    public ArgumentParser(String progname, String version) {
        this.progname = progname;
        this.version = version;
        this.options = new LinkedHashMap<String, BaseOption<?>>();
        this.shortOptions = new LinkedHashMap<Character, BaseOption<?>>();
    }

    public String getProgName() {
        return progname;
    }
    public void setProgName(String name) {
        progname = name;
    }

    public String getVersion() {
        return version;
    }
    public void setVersion(String ver) {
        version = ver;
    }

    public Collection<BaseOption<?>> getAllOptions() {
        return Collections.unmodifiableCollection(options.values());
    }

    protected <Y extends Collection<BaseOption<?>>> Y getOptions(Y list,
            boolean positional) {
        for (BaseOption<?> opt : options.values()) {
            if (opt.isPositional() == positional) list.add(opt);
        }
        return list;
    }
    public List<BaseOption<?>> getOptions() {
        return getOptions(new ArrayList<BaseOption<?>>(), false);
    }
    public List<BaseOption<?>> getArguments() {
        return getOptions(new ArrayList<BaseOption<?>>(), true);
    }

    public <X extends BaseOption<?>> X add(X opt) {
        options.put(opt.getName(), opt);
        if (opt.getShortName() != null)
            shortOptions.put(opt.getShortName(), opt);
        opt.setParser(this);
        return opt;
    }
    public boolean remove(BaseOption<?> opt) {
        shortOptions.remove(opt.getShortName());
        boolean ret = (options.remove(opt.getName()) != null);
        opt.setParser(null);
        return ret;
    }

    public void addStandardOptions() {
        add(new HelpOption());
        if (version != null) add(new VersionOption());
    }

    public BaseOption<?> getOption(ArgumentValue val) {
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
        return parse(Arrays.asList(args), true);
    }
    public ParseResult parse(Iterable<String> args) throws ParseException {
        return parse(args, true);
    }
    public ParseResult parse(Iterable<String> args, boolean full)
            throws ParseException {
        Set<BaseOption<?>> missing = getOptions(
            new HashSet<BaseOption<?>>(), false);
        Iterator<BaseOption<?>> argiter = getOptions(
            new LinkedList<BaseOption<?>>(), true).iterator();
        ArgumentSplitter splitter = new ArgumentSplitter(args);
        List<OptionValue<?>> results = new LinkedList<OptionValue<?>>();
        boolean argsOnly = false;
        BaseOption<?> opt;
        main: for (;;) {
            ArgumentValue v = splitter.next((argsOnly) ?
                ArgumentSplitter.Mode.FORCE_ARGUMENTS :
                ArgumentSplitter.Mode.OPTIONS);
            if (v == null) break;
            switch (v.getType()) {
                case LONG_OPTION: case SHORT_OPTION:
                    opt = getOption(v);
                    if (opt == null) {
                        if (full)
                            throw new ParseException("Unknown option " +
                                ((v.getType() == ArgumentValue.Type
                                    .LONG_OPTION) ? "--" : "-") +
                                v.getValue());
                        splitter.pushback(v);
                        break main;
                    } else if (opt.isPositional()) {
                        throw new ParseException("Using argument <" +
                            opt.getName() + "> as option");
                    }
                    missing.remove(opt);
                    results.add(opt.process(v, splitter));
                    break;
                case VALUE:
                    throw new ParseException("Superfluous option value: " +
                        v.getValue());
                case ARGUMENT:
                    if (v.getValue().equals("--")) {
                        argsOnly = true;
                        continue;
                    } else if (! argiter.hasNext()) {
                        if (full)
                            throw new ParseException("Superfluous " +
                                "argument: " + v.getValue());
                        splitter.pushback(v);
                        break main;
                    }
                    opt = argiter.next();
                    results.add(opt.process(v, splitter));
                    break;
                default:
                    throw new RuntimeException("Unknown ArgumentValue " +
                        "type?!");
            }
        }
        for (BaseOption<?> o : missing) {
            results.add(o.processOmitted());
        }
        while (argiter.hasNext()) {
            BaseOption<?> o = argiter.next();
            results.add(o.processOmitted());
        }
        return new ParseResult(results);
    }

}
