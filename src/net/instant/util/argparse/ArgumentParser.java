package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ArgumentParser {

    private final Set<Option<?>> options;
    private final List<Option<?>> arguments;
    private String progname;

    public ArgumentParser(String progname) {
        this.progname = progname;
        this.options = new LinkedHashSet<Option<?>>();
        this.arguments = new ArrayList<Option<?>>();
    }

    public String getProgName() {
        return progname;
    }
    public void setProgName(String name) {
        progname = name;
    }

    public Set<Option<?>> getOptions() {
        return Collections.unmodifiableSet(options);
    }

    public List<Option<?>> getArguments() {
        return Collections.unmodifiableList(arguments);
    }

    public <X> Option<X> addOption(Option<X> opt) {
        options.add(opt);
        return opt;
    }
    public boolean removeOption(Option<?> opt) {
        return options.remove(opt);
    }

    public <X> Option<X> addArgument(Option<X> arg) {
        arguments.add(arg);
        return arg;
    }
    public boolean removeArgument(Option<?> arg) {
        return arguments.remove(arg);
    }

    public ParseResult parse(String[] args) {
        return null; // NYI
    }

}
