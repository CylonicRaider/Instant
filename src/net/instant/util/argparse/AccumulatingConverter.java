package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccumulatingConverter<E> extends Converter<List<E>> {

    private static final Map<Class<?>, AccumulatingConverter<?>> registry;

    static {
        registry = new HashMap<Class<?>, AccumulatingConverter<?>>();
    }

    private final Converter<E> inner;

    public AccumulatingConverter(Converter<E> inner) {
        super(inner.getPlaceholder());
        this.inner = inner;
    }

    public Converter<E> getInner() {
        return inner;
    }

    public List<E> convert(String data) throws ParseException {
        List<E> ret = new ArrayList<E>();
        ret.add(inner.convert(data));
        return ret;
    }

    public OptionValue<List<E>> wrap(BaseOption<List<E>> option,
                                     List<E> item) {
        return new ListOptionValue<E, List<E>>(option, item);
    }

    public String format(List<E> list) {
        /* Cannot meaningfully format */
        return null;
    }

    /* HACK: Naming the method "get" would result in a "cannot override"
     *       error... */
    public static <F> AccumulatingConverter<F> getA(Class<F> cls) {
        @SuppressWarnings("unchecked")
        AccumulatingConverter<F> ret =
            (AccumulatingConverter<F>) registry.get(cls);
        if (ret == null) {
            ret = new AccumulatingConverter<F>(get(cls));
            registry.put(cls, ret);
        }
        return ret;
    }

}
