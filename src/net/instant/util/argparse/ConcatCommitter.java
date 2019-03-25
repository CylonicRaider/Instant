package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.List;

public class ConcatCommitter<E> extends Committer<List<E>> {

    public ConcatCommitter(ValueProcessor<List<E>> key) {
        super(key);
    }
    public ConcatCommitter() {
        this(null);
    }

    protected List<E> merge(List<E> oldValue, List<E> newValue) {
        if (oldValue == null) return newValue;
        List<E> ret = new ArrayList<E>();
        ret.addAll(oldValue);
        ret.addAll(newValue);
        return ret;
    }

}
