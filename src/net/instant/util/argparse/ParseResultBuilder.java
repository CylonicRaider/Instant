package net.instant.util.argparse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParseResultBuilder implements ParseResult {

    private final Map<BaseOption<?>, Object> data;

    public ParseResultBuilder() {
        data = new LinkedHashMap<BaseOption<?>, Object>();
    }

    public Map<BaseOption<?>, ?> getData() {
        return Collections.unmodifiableMap(data);
    }

    public boolean contains(BaseOption<?> opt) {
        return data.containsKey(opt);
    }

    public <X> X get(BaseOption<X> opt) {
        @SuppressWarnings("unchecked")
        X ret = (X) data.get(opt);
        return ret;
    }

    public <X> void put(BaseOption<X> key, X value) {
        data.put(key, value);
    }

}
