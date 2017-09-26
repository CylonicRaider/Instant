package net.instant.util.argparse;

public class FlagOption<X> extends Option<X> {

    private X value;

    public FlagOption(String name, Character shortName, String help) {
        super(name, shortName, help);
    }

    public X getValue() {
        return value;
    }
    public void setValue(X val) {
        value = val;
    }
    public FlagOption<X> withValue(X val) {
        value = val;
        return this;
    }

    public String formatArguments() {
        return null;
    }

    public OptionValue<X> process(ArgumentValue v, ArgumentSplitter s)
            throws ParseException {
        ArgumentValue n = s.next(ArgumentSplitter.Mode.OPTIONS);
        if (n != null && n.getType() == ArgumentValue.Type.VALUE)
            throw new ParseException("Option --" + getName() +
                " does not take arguments");
        s.pushback(n);
        return wrap(value);
    }
    protected OptionValue<X> wrap(X value) {
        return new OptionValue<X>(this, value);
    }

}
