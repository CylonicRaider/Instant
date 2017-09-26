package net.instant.util.argparse;

import java.util.List;

public class ValueArgument<X> extends Argument<X> {

    private Converter<X> converter;
    private X defaultValue;

    public ValueArgument(String name, String help, Converter<X> converter) {
        super(name, help);
        this.converter = converter;
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
        if (getDefault() != null)
            ret += " (default " + getConverter().format(getDefault()) + ")";
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

    public static <T> ValueArgument<T> of(Class<T> cls, String name,
                                          String help) {
        return new ValueArgument<T>(name, help, Converter.get(cls));
    }
    public static <T> ValueArgument<List<T>> ofList(Class<T> cls, String name,
                                                    String help) {
        return new ValueArgument<List<T>>(name, help,
                                          ListConverter.getL(cls));
    }

}
