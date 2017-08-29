package net.instant.util.argparse;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ListConverter<E> extends Converter<List<E>> {

    private static final Map<Class<?>, ListConverter<?>> registry;

    static {
        registry = new HashMap<Class<?>, ListConverter<?>>();
        registerL(String.class, ",");
        registerL(Integer.class, ",");
        registerL(File.class, File.pathSeparator);
    }

    private final Converter<E> inner;
    private final String separator;

    public ListConverter(Converter<E> inner, String separator) {
        super(inner.getPlaceholder() + '[' + separator + "...]");
        this.inner = inner;
        this.separator = separator;
    }

    public Converter<E> getInner() {
        return inner;
    }
    public String getSeparator() {
        return separator;
    }

    public List<E> convert(String data) throws ParseException {
        List<E> ret = new ArrayList<E>();
        for (String piece : data.split(Pattern.quote(getSeparator()), -1))
            ret.add(inner.convert(piece));
        return ret;
    }

    public OptionValue<List<E>> wrap(BaseOption<List<E>> option,
                                     List<E> item) {
        return new ListOptionValue<E, List<E>>(option, item);
    }

    public String format(List<E> list) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (E item : list) {
            if (first) {
                first = false;
            } else {
                sb.append(separator);
            }
            sb.append(inner.format(item));
        }
        return sb.toString();
    }

    public static <F> void registerL(Class<F> cls, ListConverter<F> cvt) {
        registry.put(cls, cvt);
    }
    public static <F> void registerL(Class<F> cls, String separator) {
        registry.put(cls, new ListConverter<F>(get(cls), separator));
    }
    public static void deregisterL(Class<?> cls) {
        registry.remove(cls);
    }
    /* HACK: Naming the method "get" would result in a "cannot override"
     *       error... */
    public static <F> ListConverter<F> getL(Class<F> cls) {
        @SuppressWarnings("unchecked")
        ListConverter<F> ret = (ListConverter<F>) registry.get(cls);
        return ret;
    }

}
