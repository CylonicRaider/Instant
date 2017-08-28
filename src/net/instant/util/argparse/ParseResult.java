package net.instant.util.argparse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParseResult {

    private final Map<Option<?>, OptionValue<?>> data;

    // Creating the mapping by subsequent additions of pairs turns out to be
    // hardly possible without compiler errors.
    public ParseResult(Iterable<OptionValue<?>> values) {
        Map<Option<?>, OptionValue<?>> data =
            new LinkedHashMap<Option<?>, OptionValue<?>>();
        // Delegated into a method to have a name for the type parameter.
        for (OptionValue<?> v : values) update(data, v);
        this.data = Collections.unmodifiableMap(data);
    }

    public Map<Option<?>, OptionValue<?>> getData() {
        return data;
    }

    public <X> X get(Option<X> opt) {
        @SuppressWarnings("unchecked")
        X ret = (X) data.get(opt);
        return ret;
    }

    private <X> void update(Map<Option<?>, OptionValue<?>> data,
                            OptionValue<X> v) {
        if (v == null) return;
        @SuppressWarnings("unchecked")
        OptionValue<X> o = (OptionValue<X>) data.get(v.getOption());
        data.put(v.getOption(), v.merge(o));
    }

}
