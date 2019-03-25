package net.instant.util.argparse;

import java.util.List;

public class Argument<T> extends BaseOption implements ValueProcessor<T> {

    private boolean optional;
    private Converter<T> converter;
    private Committer<T> committer;
    private T defaultValue;

    public Argument(String name, String help, Converter<T> converter,
                    Committer<T> committer) {
        super(name, help);
        this.converter = converter;
        this.committer = committer;
    }
    public Argument(Converter<T> converter, Committer<T> committer) {
        this(null, null, converter, committer);
    }

    public Argument<T> setup() {
        getCommitter().setKey(this);
        return this;
    }

    public Converter<T> getConverter() {
        return converter;
    }
    public void setConverter(Converter<T> c) {
        converter = c;
    }
    public Argument<T> withConverter(Converter<T> c) {
        setConverter(c);
        return this;
    }
    public Argument<T> withPlaceholder(String placeholder) {
        return withConverter(getConverter().withPlaceholder(placeholder));
    }

    public Committer<T> getCommitter() {
        return committer;
    }
    public void setCommitter(Committer<T> c) {
        committer = c;
    }
    public Argument<T> withCommitter(Committer<T> c) {
        setCommitter(c);
        return this;
    }

    public T getDefault() {
        return defaultValue;
    }
    public void setDefault(T v) {
        defaultValue = v;
    }
    public Argument<T> defaultsTo(T v) {
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

    public void startParsing(ParseResultBuilder drain)
            throws ParsingException {
        // Do not accidentally leave behind our default if we are attached to
        // a ValueOption.
        if (getDefault() != null && getCommitter().getKey() == this)
            getCommitter().commit(getDefault(), drain);
    }

    public void parse(ArgumentSplitter source, ParseResultBuilder drain)
            throws ParsingException {
        ArgumentSplitter.ArgValue av = source.next((isRequired()) ?
            ArgumentSplitter.Mode.FORCE_ARGUMENTS :
            ArgumentSplitter.Mode.ARGUMENTS);
        T value;
        if (av == null) {
            if (isRequired())
                throw new ParsingException("Missing value for",
                                           formatName());
            value = getDefault();
        } else if (av.getType() == ArgumentSplitter.ArgType.SHORT_OPTION ||
                   av.getType() == ArgumentSplitter.ArgType.LONG_OPTION) {
            source.pushback(av);
            return;
        } else {
            value = getConverter().convert(av.getValue());
        }
        getCommitter().commit(value, drain);
    }

    public void finishParsing(ParseResultBuilder drain)
            throws ParsingException {
        if (isRequired() && ! getCommitter().storedIn(drain))
            throw new ValueMissingException("Missing required",
                                            formatName());
    }

    public static <T> Argument<T> of(Class<T> cls, String name, String help) {
        return new Argument<T>(name, help, Converter.get(cls),
            new Committer<T>()).setup();
    }
    public static <T> Argument<List<T>> ofList(Class<T> cls, String name,
                                               String help) {
        return new Argument<List<T>>(name, help,
                ListConverter.getL(cls), new ConcatCommitter<T>())
            .setup();
    }

    protected static <T> Argument<T> of(Class<T> cls) {
        return new Argument<T>(Converter.get(cls), new Committer<T>());
    }
    protected static <T> Argument<List<T>> ofList(Class<T> cls) {
        return new Argument<List<T>>(ListConverter.getL(cls),
            new ConcatCommitter<T>());
    }

}
