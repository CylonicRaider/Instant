package net.instant.util.argparse;

public abstract class BaseOption<X> implements Processor {

    private String name;
    private String help;
    private boolean required;

    public BaseOption(String name, String help) {
        this.name = name;
        this.help = help;
        this.required = false;
    }

    public String getName() {
        return name;
    }
    public void setName(String n) {
        name = n;
    }

    public String getHelp() {
        return help;
    }
    public void setHelp(String h) {
        help = h;
    }

    public boolean isRequired() {
        return required;
    }
    public void setRequired(boolean r) {
        required = r;
    }
    public BaseOption<X> required() {
        setRequired(true);
        return this;
    }

    public String formatUsage() {
        StringBuilder sb = new StringBuilder();
        if (! isRequired()) sb.append('[');
        sb.append(formatUsageInner());
        if (! isRequired()) sb.append(']');
        return sb.toString();
    }
    protected abstract String formatUsageInner();

    public void startParsing(ParseResultBuilder drain)
        throws ParsingException {}
    public void finishParsing(ParseResultBuilder drain)
        throws ParsingException {}

}
