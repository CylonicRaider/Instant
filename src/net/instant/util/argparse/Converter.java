package net.instant.util.argparse;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public abstract class Converter<T> {

    private class ChangedPlaceholderConverter extends Converter<T> {

        public ChangedPlaceholderConverter(String placeholder) {
            super(placeholder);
        }

        public Converter<T> getParent() {
            return Converter.this;
        }

        public T convert(String data) throws ParsingException {
            return getParent().convert(data);
        }

    }

    private static final Map<Class<?>, Converter<?>> registry;

    static {
        registry = new HashMap<Class<?>, Converter<?>>();
        register(String.class, new Converter<String>("<STR>") {
            public String convert(String data) {
                return data;
            }
        });
        register(Integer.class, new Converter<Integer>("<INT>") {
            public Integer convert(String data) throws ParsingException {
                try {
                    return Integer.parseInt(data);
                } catch (NumberFormatException exc) {
                    throw new ParsingException("Invalid integer: " + data,
                                               exc);
                }
            }
        });
        register(File.class, new Converter<File>("<PATH>") {
            public File convert(String data) {
                return new File(data);
            }
        });
        register(KeyValue.class, new Converter<KeyValue>("<KEY>=<VALUE>") {
            public KeyValue convert(String data) {
                String[] items = data.split("=", 2);
                return new KeyValue(items[0],
                                    (items.length < 2) ? null : items[1]);
            }
        });
    }

    private final String placeholder;

    protected Converter(String placeholder) {
        this.placeholder = placeholder;
    }

    private Converter<T> getParent() {
        return this;
    }

    public String getPlaceholder() {
        return placeholder;
    }
    public Converter<T> withPlaceholder(String p) {
        // Nasty trickery: ChangedPlaceholderConverter overrides getParent()
        // to return its enclosing class (so that the same parent is used
        // when chaining withPlaceholder() calls) and delegates to that
        // parent.
        return getParent().new ChangedPlaceholderConverter(p);
    }

    public abstract T convert(String data) throws ParsingException;

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
