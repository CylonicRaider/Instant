package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConcatCommitter<E> extends Committer<List<E>> {

    public static final String COMMENT = "repeatable";

    public ConcatCommitter(ValueProcessor<List<E>> key) {
        super(key);
    }
    public ConcatCommitter() {
        this(null);
    }

    public List<String> getAddenda() {
        return Collections.singletonList(COMMENT);
    }

    protected List<E> merge(List<E> oldValue, List<E> newValue) {
        if (oldValue == null) return newValue;
        List<E> ret = new ArrayList<E>();
        ret.addAll(oldValue);
        ret.addAll(newValue);
        return ret;
    }

}
