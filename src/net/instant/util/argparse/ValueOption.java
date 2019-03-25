package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.List;

public class ValueOption<T> extends Option<Argument<T>>
        implements ValueProcessor<T> {

    private T defaultValue;

    public ValueOption(String name, Character shortName, String help,
                       Argument<T> child) {
        super(name, shortName, help, child);
    }
    public ValueOption(String name, Character shortName, String help) {
        this(name, shortName, help, null);
    }

    public ValueOption<T> setup() {
        getChild().setRequired(true);
        getChild().getCommitter().setKey(this);
        return this;
    }

    public T getDefault() {
        return defaultValue;
    }
    public void setDefault(T v) {
        defaultValue = v;
    }
    public ValueOption<T> defaultsTo(T v) {
        setDefault(v);
        return this;
    }

    public ValueOption<T> withPlaceholder(String placeholder) {
        getChild().withPlaceholder(placeholder);
        return this;
    }

    public void startParsing(ParseResultBuilder drain)
            throws ParsingException {
        super.startParsing(drain);
        if (getDefault() != null)
            getChild().getCommitter().commit(getDefault(), drain);
    }

    public void finishParsing(ParseResultBuilder drain)
            throws ParsingException {
        try {
            super.finishParsing(drain);
        } catch (ValueMissingException exc) {
            /* Explicitly swallow this one -- we use the argument's
             * "required" flag for other purposes. */
        }
        if (isRequired() && ! getChild().getCommitter().storedIn(drain))
            throw new ValueMissingException("Missing required",
                                            formatName());
    }

    public static <T> ValueOption<T> of(Class<T> cls, String name,
            Character shortname, String help) {
        return new ValueOption<T>(name, shortname, help, Argument.of(cls))
            .setup();
    }
    public static <T> ValueOption<List<T>> ofAccum(Class<T> cls, String name,
            Character shortname, String help) {
        return new ValueOption<List<T>>(
            name, shortname, help,
            new Argument<List<T>>(
                new ListConverter<T>(Converter.get(cls), null),
                new Committer<List<T>>()
            )
        ).setup().defaultsTo(new ArrayList<T>());
    }
    public static <T> ValueOption<List<T>> ofList(Class<T> cls, String name,
            Character shortname, String help) {
        return new ValueOption<List<T>>(name, shortname, help,
            Argument.ofList(cls)).setup();
    }

}
