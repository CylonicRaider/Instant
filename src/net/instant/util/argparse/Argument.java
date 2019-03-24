package net.instant.util.argparse;

import java.util.List;

public class Argument<X> extends BaseOption<X> {

    private boolean optional;
    private Converter<X> converter;
    private Committer<X> committer;
    private X defaultValue;

    public Argument(String name, String help, Converter<X> converter,
                    Committer<X> committer) {
        super(name, help);
        this.converter = converter;
        this.committer = committer;
        committer.setKey(this);
    }
    public Argument(Converter<X> converter, Committer<X> committer) {
        this(null, null, converter, committer);
    }

    public Converter<X> getConverter() {
        return converter;
    }
    public void setConverter(Converter<X> c) {
        converter = c;
    }
    public Argument<X> withConverter(Converter<X> c) {
        setConverter(c);
        return this;
    }

    public Committer<X> getCommitter() {
        return committer;
    }
    public void setCommitter(Committer<X> c) {
        committer = c;
        c.setKey(this);
    }
    public Argument<X> withCommitter(Committer<X> c) {
        setCommitter(c);
        return this;
    }

    public X getDefault() {
        return defaultValue;
    }
    public void setDefault(X v) {
        defaultValue = v;
    }
    public Argument<X> defaultsTo(X v) {
        setDefault(v);
        return this;
    }

    public String formatName() {
        String name = getName();
        if (name == null) name = "anonymous";
        return "argument <" + name + ">";
    }

    protected String formatUsageInner() {
        String name = getName();
        if (name == null) return getConverter().getPlaceholder();
        return "<" + name + ">";
    }

    public HelpLine getHelpLine() {
        String name = getName();
        return new HelpLine(formatUsageInner(), ":",
            getConverter().getPlaceholder(), getHelp());
    }

    public void parse(ArgumentSplitter source, ParseResultBuilder drain)
            throws ParsingException {
        ArgumentSplitter.ArgValue av = source.next((isRequired()) ?
            ArgumentSplitter.Mode.FORCE_ARGUMENTS :
            ArgumentSplitter.Mode.ARGUMENTS);
        X value;
        if (av == null) {
            if (isRequired())
                throw new ParsingException("Missing value for", formatName());
            value = getDefault();
        } else if (av.getType() == ArgumentSplitter.ArgType.SHORT_OPTION ||
                   av.getType() == ArgumentSplitter.ArgType.LONG_OPTION) {
            source.pushback(av);
            return;
        } else {
            value = converter.convert(av.getValue());
        }
        committer.commit(value, drain);
    }

    public void finishParsing(ParseResultBuilder drain)
            throws ParsingException {
        if (! isRequired()) {
            /* NOP */
        } else if (! committer.storedIn(drain)) {
            throw new ParsingException("Missing required value for",
                                       formatName());
        } else {
            committer.commit(getDefault(), drain);
        }
    }

    public static <T> Argument<T> of(Class<T> cls, String name, String help) {
        return new Argument<T>(name, help, Converter.get(cls),
            new Committer<T>());
    }
    public static <T> Argument<List<T>> ofList(Class<T> cls, String name,
                                               String help) {
        return new Argument<List<T>>(name, help,
            ListConverter.getL(cls), new ConcatCommitter<T>());
    }

    protected static <T> Argument<T> of(Class<T> cls) {
        return new Argument<T>(Converter.get(cls), new Committer<T>());
    }
    protected static <T> Argument<List<T>> ofList(Class<T> cls) {
        return new Argument<List<T>>(ListConverter.getL(cls),
            new ConcatCommitter<T>());
    }

}
