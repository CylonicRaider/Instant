package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.List;

public class Option<T> extends BaseOption<Argument<T>>
        implements ValueProcessor<T> {

    private T defaultValue;

    public Option(String name, Character shortName, String help,
                  Argument<T> child) {
        super(name, shortName, help, child);
    }
    public Option(String name, Character shortName, String help) {
        this(name, shortName, help, null);
    }

    public Option<T> required() {
        super.required();
        return this;
    }
    public Option<T> optional() {
        super.optional();
        return this;
    }
    public Option<T> withComment(String comment) {
        super.withComment(comment);
        return this;
    }
    public Option<T> withChild(Argument<T> c) {
        super.withChild(c);
        return this;
    }

    public T getDefault() {
        return defaultValue;
    }
    public void setDefault(T v) {
        defaultValue = v;
    }
    public Option<T> defaultsTo(T v) {
        setDefault(v);
        return this;
    }

    public Option<T> withPlaceholder(String placeholder) {
        getChild().withPlaceholder(placeholder);
        return this;
    }

    public Option<T> setup() {
        getChild().setRequired(true);
        getChild().getCommitter().setKey(this);
        return this;
    }

    public HelpLine getHelpLine() {
        HelpLine ret = super.getHelpLine();
        if (ret != null)
            ret.addAddendum(Argument.formatDefault(Argument.DEFAULT_PREFIX,
                getChild().getConverter(), getDefault()));
        return ret;
    }

    public void startParsing(ParseResultBuilder drain)
            throws ParsingException {
        Committer<T> comm = getChild().getCommitter();
        T oldValue = comm.get(drain);
        super.startParsing(drain);
        if (oldValue == null) {
            comm.remove(drain);
        } else {
            comm.put(oldValue, drain);
        }
        if (getDefault() != null) {
            comm.commit(getDefault(), drain);
        }
    }

    public void finishParsing(ParseResultBuilder drain)
            throws ParsingException {
        try {
            super.finishParsing(drain);
        } catch (ValueMissingException exc) {
            /* Explicitly swallow this one -- we use the argument's
             * "required" flag for other purposes. */
        }
        if (isRequired() && ! getChild().getCommitter().containedIn(drain))
            throw new ValueMissingException("Missing required",
                                            formatName());
    }

    public static <T> Option<T> of(Class<T> cls, String name,
            Character shortname, String help) {
        return new Option<T>(name, shortname, help, Argument.of(cls))
            .setup();
    }
    public static <T> Option<List<T>> ofAccum(Class<T> cls, String name,
            Character shortname, String help) {
        return new Option<List<T>>(
            name, shortname, help,
            new Argument<List<T>>(
                new ListConverter<T>(Converter.get(cls), null),
                new ConcatCommitter<T>()
            )
        ).setup().defaultsTo(new ArrayList<T>());
    }
    public static <T> Option<List<T>> ofList(Class<T> cls, String name,
            Character shortname, String help) {
        return new Option<List<T>>(name, shortname, help,
            Argument.ofList(cls)).setup();
    }

}
