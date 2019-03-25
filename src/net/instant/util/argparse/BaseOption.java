package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseOption implements Processor {

    private String name;
    private String help;
    private boolean required;
    private final List<String> comments;

    public BaseOption(String name, String help) {
        this.name = name;
        this.help = help;
        this.required = false;
        this.comments = new ArrayList<String>();
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
    public BaseOption required() {
        setRequired(true);
        return this;
    }
    public BaseOption optional() {
        setRequired(false);
        return this;
    }

    public List<String> getComments() {
        return comments;
    }
    public BaseOption withComment(String comment) {
        if (comment != null) comments.add(comment);
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
