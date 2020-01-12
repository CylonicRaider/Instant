package net.instant.util;

import java.util.Collection;
import java.util.IdentityHashMap;

public class IdentityLinkedSet<E> extends LinkedSet<E> {

    public IdentityLinkedSet(Collection<? extends E> data) {
        super(new IdentityHashMap<E, Node>(), data);
    }
    public IdentityLinkedSet() {
        this(null);
    }

}
