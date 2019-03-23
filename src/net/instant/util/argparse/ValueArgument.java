package net.instant.util.argparse;

import java.util.List;

public class ValueArgument<X> extends Argument<X> implements Processor {

    private boolean optional;
    private Converter<X> converter;
    private Committer<X> committer;
    private X defaultValue;

    public ValueArgument(String name, String help, Converter<X> converter) {
        super(name, help);
        this.converter = converter;
    }
    public ValueArgument(String name, String help, Converter<X> converter,
                         Committer<X> committer) {
        super(name, help);
        this.converter = converter;
        this.committer = committer;
        if (committer != null) committer.setKey(this);
    }

    public boolean isOptional() {
        return optional;
    }
    public void setOptional(boolean o) {
        optional = o;
    }

    public Converter<X> getConverter() {
        return converter;
    }
    public void setConverter(Converter<X> c) {
        converter = c;
    }
    public ValueArgument<X> withConverter(Converter<X> c) {
        converter = c;
        return this;
    }
    public ValueArgument<X> withPlaceholder(String placeholder) {
        converter = converter.withPlaceholder(placeholder);
        return this;
    }

    public Committer<X> getCommitter() {
        return committer;
    }
    public void setCommitter(Committer<X> c) {
        committer = c;
    }
    public ValueArgument<X> withCommitter(Committer<X> c) {
        committer = c;
        c.setKey(this);
        return this;
    }

    public X getDefault() {
        return defaultValue;
    }
    public void setDefault(X v) {
        defaultValue = v;
    }
    public ValueArgument<X> defaultsTo(X v) {
        defaultValue = v;
        return this;
    }

    public String formatArguments() {
        return getConverter().getPlaceholder();
    }
    public String formatHelp() {
        String ret = super.formatHelp();
        String fmtDef = getConverter().format(getDefault());
        if (fmtDef != null) ret += " (default " + fmtDef + ")";
        return ret;
    }

    public OptionValue<X> process(ArgumentValue v, ArgumentSplitter s)
            throws ParseException {
        return converter.wrap(this, converter.convert(v.getValue()));
    }
    public OptionValue<X> processOmitted() throws ParseException {
        super.processOmitted();
        if (getDefault() != null)
            return converter.wrap(this, getDefault());
        return null;
    }

    public void parse(ArgumentSplitter source, ParseResultBuilder drain)
            throws ParsingException {
        ArgumentValue av = source.next((isOptional()) ?
            ArgumentSplitter.Mode.ARGUMENTS :
            ArgumentSplitter.Mode.FORCE_ARGUMENTS);
        X value;
        if (av == null) {
            if (! isOptional())
                throw new ParsingException("Missing value for",
                                           formatName());
            value = getDefault();
        } else if (av.getType() == ArgumentValue.Type.SHORT_OPTION ||
                   av.getType() == ArgumentValue.Type.LONG_OPTION) {
            source.pushback(av);
            return;
        } else {
            try {
                value = converter.convert(av.getValue());
            } catch (ParseException exc) {
                throw new ParsingException(exc.getMessage(), formatName(),
                                           exc);
            }
        }
        committer.commit(value, drain);
    }

    public void finishParsing(ParseResultBuilder drain)
            throws ParsingException {
        if (isOptional()) {
            /* NOP */
        } else if (! committer.storedIn(drain)) {
            throw new ParsingException("Missing required value for",
                                       formatName());
        } else {
            committer.commit(getDefault(), drain);
        }
    }

    public static <T> ValueArgument<T> of(Class<T> cls, String name,
                                          String help) {
        return new ValueArgument<T>(name, help, Converter.get(cls),
            new Committer<T>());
    }
    public static <T> ValueArgument<List<T>> ofList(Class<T> cls, String name,
                                                    String help) {
        return new ValueArgument<List<T>>(name, help,
            ListConverter.getL(cls), new ConcatCommitter<T>());
    }

}
