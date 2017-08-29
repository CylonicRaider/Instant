package net.instant.util.argparse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParseResult {

    private final Map<BaseOption<?>, OptionValue<?>> data;

    // Creating the mapping by subsequent additions of pairs turns out to be
    // hardly possible without compiler errors.
    public ParseResult(Iterable<OptionValue<?>> values) {
        Map<BaseOption<?>, OptionValue<?>> data =
            new LinkedHashMap<BaseOption<?>, OptionValue<?>>();
        // Delegated into a method to have a name for the type parameter.
        for (OptionValue<?> v : values) update(data, v);
        this.data = Collections.unmodifiableMap(data);
    }

    public Map<BaseOption<?>, OptionValue<?>> getData() {
        return data;
    }

    public <X> X get(BaseOption<X> opt) {
        @SuppressWarnings("unchecked")
        X ret = (X) data.get(opt);
        return ret;
    }

    private <X> void update(Map<BaseOption<?>, OptionValue<?>> data,
                            OptionValue<X> v) {
        if (v == null) return;
        @SuppressWarnings("unchecked")
        OptionValue<X> o = (OptionValue<X>) data.get(v.getOption());
        data.put(v.getOption(), v.merge(o));
    }

}
