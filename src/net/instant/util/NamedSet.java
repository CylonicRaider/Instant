package net.instant.util;

import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class NamedSet<E extends NamedValue> extends AbstractSet<E>
        implements NamedValue {

    private final String name;
    private final Set<E> data;

    public NamedSet(String name, Set<E> data) {
        if (name == null)
            throw new NullPointerException("NamedSet name may not be null");
        if (data == null)
            throw new NullPointerException(
                "NamedSet backing data may not be null");
        this.name = name;
        this.data = data;
        validateBackingSet(data);
    }
    public NamedSet(String name) {
        this(name, new HashSet<E>());
    }

    protected void validateBackingSet(Set<E> set) {
        for (E item : set) {
            // Intentionally permitting NPE-s.
            if (! item.getName().equals(getName()))
                throw new IllegalArgumentException(
                    "NamedSet backing data contain invalid elements");
        }
    }

    public String getName() {
        return name;
    }

    public int size() {
        return data.size();
    }

    public Iterator<E> iterator() {
        // Iterator does not implement addition so exposing this is fine.
        return data.iterator();
    }

    public boolean contains(Object elem) {
        return data.contains(elem);
    }

    public boolean add(E elem) {
        if (! getName().equals(elem.getName()))
            throw new IllegalArgumentException(
                "Adding mismatching value to NamedSet");
        return data.add(elem);
    }

    public boolean remove(Object obj) {
        return data.remove(obj);
    }

    public void clear() {
        data.clear();
    }

}
