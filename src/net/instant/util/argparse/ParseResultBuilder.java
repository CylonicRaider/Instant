package net.instant.util.argparse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParseResultBuilder implements ParseResult {

    private final Map<ValueProcessor<?>, Object> data;

    public ParseResultBuilder() {
        data = new LinkedHashMap<ValueProcessor<?>, Object>();
    }

    public Map<ValueProcessor<?>, Object> getData() {
        return Collections.unmodifiableMap(data);
    }

    public boolean contains(ValueProcessor<?> key) {
        return data.containsKey(key);
    }

    public <T> T get(ValueProcessor<T> key) {
        @SuppressWarnings("unchecked")
        T ret = (T) data.get(key);
        return ret;
    }

    public <T> void put(ValueProcessor<T> key, T value) {
        data.put(key, value);
    }

    public void remove(ValueProcessor<?> key) {
        data.remove(key);
    }

}
