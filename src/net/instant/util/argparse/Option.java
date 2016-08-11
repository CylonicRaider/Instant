package net.instant.util.argparse;

public abstract class Option<T> {

    private final String name;
    private final int numArguments;
    private final boolean positional;
    private final boolean required;
    private T defaultValue;
    private ArgParser parent;
    private String help;

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
    public ArgParser getParent() {
        return parent;
    }
    public String getHelp() {
        return help;
    }

    public void setDefault(T d) {
        defaultValue = d;
    }
    public void setParent(ArgParser p) {
        parent = p;
    }
    public void setHelp(String h) {
        help = h;
    }

    protected OptionValue<T> wrap(T value) {
        return new OptionValue<T>(this, value);
    }

    public abstract String getPlaceholder(int index);
    public abstract OptionValue<T> parse(OptionValue<T> old,
        String[] arguments) throws ParseException;

    public T get(ParseResult r) {
        OptionValue<T> v = r.get(this);
        return (v == null) ? defaultValue : v.getValue();
    }

    protected String formatArgs() {
        StringBuilder sb = new StringBuilder();
        int n = getNumArguments();
        for (int i = 0; i < n; i++) {
            sb.append((i == 0) ? "<" : " <");
            sb.append(getPlaceholder(i));
            sb.append('>');
        }
        return sb.toString();
    }
    public String formatUsage() {
        String res;
        if (isPositional()) {
            res = "<" + getName() + ">";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("--");
            sb.append(getName());
            if (getNumArguments() > 0) {
                sb.append(' ');
                sb.append(formatArgs());
            }
            res = sb.toString();
        }
        if (! isRequired()) res = "[" + res + "]";
        return res;
    }
    public String[] formatHelp() {
        String name, col, args = formatArgs(), help = getHelp();
        if (isPositional()) {
            name = "<" + getName() + ">";
            col = (args.isEmpty()) ? "" : ":";
        } else {
            name = "--" + getName();
            col = "";
        }
        if (help == null) help = "???";
        return new String[] { name, col, args, help };
    }

}
