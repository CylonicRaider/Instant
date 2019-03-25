package net.instant.util.argparse;

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

    public boolean storedIn(ParseResult store) {
        return store.contains(key);
    }

    public T retrieve(ParseResult store) {
        return store.get(key);
    }

    public void commit(T value, ParseResultBuilder store) {
        store.put(key, merge(store.get(key), value));
    }

    public void remove(ParseResultBuilder store) {
        store.remove(key);
    }

    protected T merge(T oldValue, T newValue) {
        return newValue;
    }

}
