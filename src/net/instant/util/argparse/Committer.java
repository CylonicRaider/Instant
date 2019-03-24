package net.instant.util.argparse;

public class Committer<T> {

    private BaseOption<T> key;

    public Committer(BaseOption<T> key) {
        this.key = key;
    }
    public Committer() {
        this(null);
    }

    public BaseOption<T> getKey() {
        return key;
    }
    public void setKey(BaseOption<T> k) {
        key = k;
    }

    public boolean storedIn(ParseResult store) {
        return store.contains(key);
    }

    public T retrieve(ParseResult store) {
        return store.get(key);
    }

    public void commit(T value, ParseResultBuilder store) {
        store.put(key, value);
    }

}
