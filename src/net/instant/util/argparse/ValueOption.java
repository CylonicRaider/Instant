package net.instant.util.argparse;

import java.util.List;

public class ValueOption<X> extends Option<X> {

    private Converter<X> converter;
    private X defaultValue;
    private boolean optional;
    private X optionalDefault;

    public ValueOption(String name, Character shortName, String help,
                       Converter<X> converter) {
        super(name, shortName, help);
        this.converter = converter;
    }

    public Converter<X> getConverter() {
        return converter;
    }
    public void setConverter(Converter<X> c) {
        converter = c;
    }
    public ValueOption<X> withConverter(Converter<X> c) {
        converter = c;
        return this;
    }

    public X getDefault() {
        return defaultValue;
    }
    public void setDefault(X v) {
        defaultValue = v;
    }
    public ValueOption<X> defaultsTo(X v) {
        defaultValue = v;
        return this;
    }

    public boolean isOptional() {
        return optional;
    }
    public void setOptional(boolean o) {
        optional = o;
    }

    public X getOptionalDefault() {
        return optionalDefault;
    }
    public void setOptionalDefault(X v) {
        optionalDefault = v;
    }
    public ValueOption<X> optionalWith(X value) {
        optional = true;
        optionalDefault = value;
        return this;
    }

    public String formatArguments() {
        StringBuilder sb = new StringBuilder();
        if (isOptional()) sb.append('[');
        sb.append(getConverter().getPlaceholder());
        if (isOptional()) sb.append(']');
        return sb.toString();
    }
    public String formatHelp() {
        String ret = super.formatHelp();
        if (getDefault() != null)
            ret += " (default " + getConverter().format(getDefault()) + ")";
        return ret;
    }

    public OptionValue<X> process(ArgumentParser p, ArgumentValue v,
                                  ArgumentSplitter s) throws ParseException {
        ArgumentValue a = s.next((isOptional()) ?
            ArgumentSplitter.Mode.ARGUMENTS :
            ArgumentSplitter.Mode.FORCE_ARGUMENTS);
        boolean valueAbsent = true;
        if (a != null) {
            if (a.getType() == ArgumentValue.Type.VALUE ||
                    a.getType() == ArgumentValue.Type.ARGUMENT) {
                return converter.wrap(this, converter.convert(a.getValue()));
            } else {
                s.pushback(a);
            }
        }
        if (! isOptional())
            throw new ParseException("Missing required argument for " +
                "option --" + v.getValue());
        return converter.wrap(this, optionalDefault);
    }

    public static <T> ValueOption<T> of(Class<T> cls, String name,
            Character shortname, String help) {
        return new ValueOption<T>(name, shortname, help, Converter.get(cls));
    }
    public static <T> ValueOption<List<T>> ofList(Class<T> cls, String name,
            Character shortname, String help) {
        return new ValueOption<List<T>>(name, shortname, help,
                                        ListConverter.getL(cls));
    }

}
