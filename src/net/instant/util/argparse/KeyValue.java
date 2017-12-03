package net.instant.util.argparse;

import java.util.Map;

public class KeyValue implements Map.Entry<String, String> {

    private final String key;
    private final String value;

    public KeyValue(String key, String value) {
        if (key == null)
            throw new NullPointerException("KeyValue key must not be null");
        this.key = key;
        this.value = value;
    }
    public KeyValue(Map.Entry<String, String> entry) {
        this(entry.getKey(), entry.getValue());
    }

    public boolean equals(Object o) {
        if (! (o instanceof Map.Entry)) return false;
        Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
        return key.equals(e.getKey()) && ((value == null) ?
            e.getValue() == null : value.equals(e.getValue()));
    }

    public int hashCode() {
        return key.hashCode() ^ ((value == null) ? 0 : value.hashCode());
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String setValue(String ignored) {
        throw new UnsupportedOperationException("KeyValue is immutable");
    }

}
