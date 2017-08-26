package net.instant.util.argparse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParseResult {

    private final Map<Option<?>, OptionValue<?>> data;

    public ParseResult(Iterable<OptionValue<?>> values) {
        Map<Option<?>, OptionValue<?>> data =
            new LinkedHashMap<Option<?>, OptionValue<?>>();
        for (OptionValue<?> v : values) data.put(v.getOption(), v);
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

}
