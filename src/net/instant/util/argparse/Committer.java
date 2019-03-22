package net.instant.util.argparse;

public class Committer<T> {

    private final BaseOption<T> key;

    public Committer(BaseOption<T> key) {
        this.key = key;
    }

    public BaseOption<T> getKey() {
        return key;
    }

    public void commit(T value, ParseResultBuilder drain) {
        drain.put(new OptionValue<T>(key, value));
    }

}
