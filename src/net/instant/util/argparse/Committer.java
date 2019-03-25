package net.instant.util.argparse;

import java.util.Collections;
import java.util.List;

public class Committer<T> {

    private ValueProcessor<T> key;

    public Committer(ValueProcessor<T> key) {
        this.key = key;
    }
    public Committer() {
        this(null);
    }

    public ValueProcessor<T> getKey() {
        return key;
    }
    public void setKey(ValueProcessor<T> k) {
        key = k;
    }

    public List<String> getAddenda() {
        return Collections.emptyList();
    }

    public boolean containedIn(ParseResult store) {
        return store.contains(key);
    }
    public T get(ParseResult store) {
        return store.get(key);
    }
    public void put(T value, ParseResultBuilder store) {
        store.put(key, value);
    }
    public void remove(ParseResultBuilder store) {
        store.remove(key);
    }

    public void commit(T value, ParseResultBuilder store) {
        put(merge(get(store), value), store);
    }
    protected T merge(T oldValue, T newValue) {
        return newValue;
    }

}
