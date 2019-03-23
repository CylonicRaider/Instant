package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.List;

public class ConcatCommitter<E> extends Committer<List<E>> {

    public ConcatCommitter(BaseOption<List<E>> key) {
        super(key);
    }
    public ConcatCommitter() {
        this(null);
    }

    public void commit(List<E> value, ParseResultBuilder store) {
        List<E> oldValue = store.get(getKey());
        List<E> newValue;
        if (oldValue == null) {
            newValue = value;
        } else {
            newValue = new ArrayList<E>();
            newValue.addAll(oldValue);
            newValue.addAll(value);
        }
        super.commit(newValue, store);
    }

}
