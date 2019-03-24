package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.List;

public class Option<X> extends BaseOption<X> {

    private Character shortName;
    private Processor child;

    public Option(String name, Character shortName, String help,
                  Processor child) {
        super(name, help);
        this.shortName = shortName;
        this.child = child;
    }
    public Option(String name, Character shortName, String help) {
        this(name, shortName, help, null);
    }

    public Character getShortName() {
        return shortName;
    }
    public void setShortName(Character sn) {
        shortName = sn;
    }

    public Processor getChild() {
        return child;
    }
    public void setChild(Processor c) {
        child = c;
    }
    public Option<X> withChild(Processor c) {
        setChild(c);
        return this;
    }

    public Option<X> defaultsTo(X value) {
        if (! (child instanceof Argument))
            throw new IllegalStateException(
                "Cannot set default of non-value option");
        @SuppressWarnings("unchecked")
        Argument<X> arg = (Argument<X>) child;
        arg.setDefault(value);
        return this;
    }

    public String formatName() {
        return "option --" + getName();
    }

    protected String formatUsageInner() {
        StringBuilder sb = new StringBuilder("--");
        sb.append(getName());
        if (getShortName() != null) sb.append("|-").append(getShortName());
        String childUsage = getChild().formatUsage();
        if (childUsage != null) sb.append(' ').append(childUsage);
        return sb.toString();
    }

    public HelpLine getHelpLine() {
        HelpLine ret = new HelpLine("--" + getName(), null, getHelp());
        HelpLine childHelp = child.getHelpLine();
        if (childHelp != null) {
            ret.setParams(childHelp.getParams());
            ret.getAddenda().addAll(childHelp.getAddenda());
        }
        return ret;
    }

    public boolean matches(ArgumentSplitter.ArgValue av) {
        switch (av.getType()) {
            case SHORT_OPTION:
                return (getShortName() != null &&
                        av.getValue().charAt(0) == getShortName());
            case LONG_OPTION:
                return getName().equals(av.getValue());
            default:
                return false;
        }
    }

    public void startParsing(ParseResultBuilder drain)
            throws ParsingException {
        getChild().startParsing(drain);
    }

    public void parse(ArgumentSplitter source, ParseResultBuilder drain)
            throws ParsingException {
        ArgumentSplitter.ArgValue check = source.next(
            ArgumentSplitter.Mode.OPTIONS);
        if (check != null && ! matches(check)) {
            source.pushback(check);
            throw new ParsingException("Command-line " + check +
                "does not match", formatName());
        }
        getChild().parse(source, drain);
    }

    public void finishParsing(ParseResultBuilder drain)
            throws ParsingException {
        getChild().finishParsing(drain);
    }

    public static <T> Option<T> of(Class<T> cls, String name,
            Character shortname, String help) {
        return new Option<T>(name, shortname, help, Argument.of(cls));
    }
    public static <T> Option<List<T>> ofAccum(Class<T> cls, String name,
            Character shortname, String help) {
        return new Option<List<T>>(
            name, shortname, help,
            new Argument<List<T>>(
                new ListConverter<T>(Converter.get(cls), null),
                new Committer<List<T>>()
            )
        ).defaultsTo(new ArrayList<T>());
    }
    public static <T> Option<List<T>> ofList(Class<T> cls, String name,
            Character shortname, String help) {
        return new Option<List<T>>(name, shortname, help,
            Argument.ofList(cls));
    }

}
