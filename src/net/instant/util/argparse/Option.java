package net.instant.util.argparse;

public abstract class Option<T> {

    private final String name;
    private final int numArguments;
    private final boolean positional;
    private final boolean required;
    private T defaultValue;

    public Option(String name, int numArguments, boolean positional,
                  boolean required, T defaultValue) {
        this.name = name;
        this.numArguments = numArguments;
        this.positional = positional;
        this.required = required;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }
    public int getNumArguments() {
        return numArguments;
    }
    public boolean isPositional() {
        return positional;
    }
    public boolean isRequired() {
        return required;
    }
    public T getDefault() {
        return defaultValue;
    }

    public void setDefault(T d) {
        defaultValue = d;
    }

    protected OptionValue<T> wrap(T value) {
        return new OptionValue<T>(this, value);
    }

    public abstract OptionValue<T> parse(OptionValue<T> old,
        String[] arguments) throws ParseException;

    public T get(ParseResult r) {
        OptionValue<T> v = r.get(this);
        return (v == null) ? defaultValue : v.getValue();
    }

}
