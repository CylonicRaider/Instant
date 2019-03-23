package net.instant.util.argparse;

public class ConstantStoreAction<T> extends Action {

    private T value;
    private Committer<T> committer;

    public ConstantStoreAction(T value, Committer<T> committer) {
        this.value = value;
        this.committer = committer;
    }

    public T getValue() {
        return value;
    }
    public void setValue(T val) {
        value = val;
    }

    public Committer<T> getCommitter() {
        return committer;
    }
    public void setCommitter(Committer<T> comm) {
        committer = comm;
    }

    public void parse(ArgumentSplitter source, ParseResultBuilder drain)
            throws ParsingException {
        super.parse(source, drain);
        committer.commit(value, drain);
    }

}
