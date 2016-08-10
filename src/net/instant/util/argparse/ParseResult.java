package net.instant.util.argparse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParseResult {

    private final Map<Option<?>, OptionValue<?>> values;

    public ParseResult(Iterable<OptionValue<?>> values) {
        Map<Option<?>, OptionValue<?>> m =
            new LinkedHashMap<Option<?>, OptionValue<?>>();
        for (OptionValue<?> v : values) {
            m.put(v.getOption(), v);
        }
        this.values = Collections.unmodifiableMap(m);
    }

    public Map<Option<?>, OptionValue<?>> getValues() {
        return values;
    }

    public <T> OptionValue<T> get(Option<T> opt) {
        @SuppressWarnings("unchecked")
        OptionValue<T> ret = (OptionValue<T>) values.get(opt);
        return ret;
    }

}
