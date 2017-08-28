package net.instant.util.argparse;

public abstract class ValueOption<X> extends Option<X> {

    private X defaultValue;
    private boolean optional;
    private X optionalDefault;
    private String placeholder;

    public ValueOption(String name, Character shortName, String help) {
        super(name, shortName, help);
    }

    public X getDefault() {
        return defaultValue;
    }
    public void setDefault(X v) {
        defaultValue = v;
    }
    public ValueOption<X> defaultsTo(X v) {
        defaultValue = v;
        return this;
    }

    public boolean isOptional() {
        return optional;
    }
    public void setOptional(boolean o) {
        optional = o;
    }

    public X getOptionalDefault() {
        return optionalDefault;
    }
    public void setOptionalDefault(X v) {
        optionalDefault = v;
    }
    public ValueOption<X> optionalWith(X value) {
        optional = true;
        optionalDefault = value;
        return this;
    }

    public String getPlaceholder() {
        return placeholder;
    }
    public void setPlaceholder(String p) {
        placeholder = p;
    }
    public ValueOption<X> withPlaceholder(String p) {
        placeholder = p;
        return this;
    }
    protected abstract String getDefaultPlaceholder();

    public String formatArguments() {
        String pl = getPlaceholder();
        if (pl == null) pl = getDefaultPlaceholder();
        return "<" + pl + ">";
    }
    public String formatHelp() {
        String ret = super.formatHelp();
        String def = formatDefault();
        if (def != null) ret += " (default " + def + ")";
        return ret;
    }
    protected String formatDefault() {
        X def = getDefault();
        return (def == null) ? null : String.valueOf(def);
    }

    public OptionValue<X> process(ArgumentParser p, ArgumentValue v,
                                  ArgumentSplitter s) throws ParseException {
        ArgumentValue a = s.next((isOptional()) ?
            ArgumentSplitter.Mode.ARGUMENTS :
            ArgumentSplitter.Mode.FORCE_ARGUMENTS);
        boolean valueAbsent = true;
        if (a != null) {
            if (a.getType() == ArgumentValue.Type.VALUE ||
                    a.getType() == ArgumentValue.Type.ARGUMENT) {
                return wrap(parse(a.getValue()));
            } else {
                s.pushback(a);
            }
        }
        if (! isOptional())
            throw new ParseException("Missing required argument for " +
                "option --" + v.getValue());
        return wrap(optionalDefault);
    }
    protected abstract X parse(String data) throws ParseException;
    protected OptionValue<X> wrap(X item) {
        return new OptionValue<X>(this, item);
    }

}
