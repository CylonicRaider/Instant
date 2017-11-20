package net.instant.util.argparse;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public abstract class Converter<T> {

    private static final Map<Class<?>, Converter<?>> registry;

    static {
        registry = new HashMap<Class<?>, Converter<?>>();
        register(String.class, new Converter<String>("<str>") {
            public String convert(String data) {
                return data;
            }
        });
        register(Integer.class, new Converter<Integer>("<int>") {
            public Integer convert(String data) throws ParseException {
                try {
                    return Integer.parseInt(data);
                } catch (NumberFormatException exc) {
                    throw new ParseException("Invalid integer: " + data,
                                             exc);
                }
            }
        });
        register(File.class, new Converter<File>("<path>") {
            public File convert(String data) {
                return new File(data);
            }
        });
    }

    private final String placeholder;

    protected Converter(String placeholder) {
        this.placeholder = placeholder;
    }

    public String getPlaceholder() {
        return placeholder;
    }
    public Converter<T> withPlaceholder(String p) {
        return new Converter<T>(p) {
            public T convert(String data) throws ParseException {
                return Converter.this.convert(data);
            }
        };
    }

    public abstract T convert(String data) throws ParseException;

    public OptionValue<T> wrap(BaseOption<T> option, T item) {
        return new OptionValue<T>(option, item);
    }

    public String format(T item) {
        if (item == null) return null;
        return String.valueOf(item);
    }

    public static <X> void register(Class<X> cls, Converter<X> cvt) {
        registry.put(cls, cvt);
    }
    public static void deregister(Class<?> cls) {
        registry.remove(cls);
    }
    public static <X> Converter<X> get(Class<X> cls) {
        @SuppressWarnings("unchecked")
        Converter<X> ret = (Converter<X>) registry.get(cls);
        return ret;
    }

}
