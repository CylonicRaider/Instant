package net.instant.util.argparse;

public class BaseOption<X extends Processor> extends StandardProcessor {

    private Character shortName;
    private X child;

    public BaseOption(String name, Character shortName, String help,
                      X child) {
        super(name, help);
        this.shortName = shortName;
        this.child = child;
    }
    public BaseOption(String name, Character shortName, String help) {
        this(name, shortName, help, null);
    }

    public Character getShortName() {
        return shortName;
    }
    public void setShortName(Character sn) {
        shortName = sn;
    }

    public X getChild() {
        return child;
    }
    public void setChild(X c) {
        child = c;
    }
    public BaseOption<X> withChild(X c) {
        setChild(c);
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
        ret.getAddenda().addAll(getComments());
        HelpLine childHelp = getChild().getHelpLine();
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
        try {
            getChild().startParsing(drain);
        } catch (ParsingException exc) {
            rethrow(exc);
        }
    }

    public void parse(ArgumentSplitter source, ParseResultBuilder drain)
            throws ParsingException {
        ArgumentSplitter.ArgValue check = source.peek(
            ArgumentSplitter.Mode.OPTIONS);
        if (check != null && ! matches(check)) {
            throw new ParsingException("Command-line " + check +
                "does not match", formatName());
        }
        source.next(ArgumentSplitter.Mode.OPTIONS);
        try {
            getChild().parse(source, drain);
        } catch (ParsingException exc) {
            rethrow(exc);
        }
    }

    public void finishParsing(ParseResultBuilder drain)
            throws ParsingException {
        try {
            getChild().finishParsing(drain);
        } catch (ParsingException exc) {
            rethrow(exc);
        }
    }

    protected void rethrow(ParsingException exc) throws ParsingException {
        if (exc instanceof ValueMissingException) {
            throw new ValueMissingException((ValueMissingException) exc,
                                            formatName());
        } else {
            throw new ParsingException(exc, formatName());
        }
    }

}
