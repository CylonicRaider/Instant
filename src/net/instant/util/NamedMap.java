package net.instant.util;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.instant.api.NamedValue;

public class NamedMap<V extends NamedValue> extends AbstractMap<String, V> {

    protected class ValueCollection extends AbstractCollection<V> {

        private final Collection<V> values;

        public ValueCollection() {
            values = NamedMap.this.data.values();
        }

        public int size() {
            return values.size();
        }

        public Iterator<V> iterator() {
            return values.iterator();
        }

        public boolean contains(Object obj) {
            return containsValue(obj);
        }

        public boolean add(V value) {
            V oldValue = data.put(getNameOf(value), value);
            // Map specifies that put() always displaces the old mapping, so
            // the map does not change only if value itself is already there.
            return (value != oldValue);
        }

        public boolean remove(V value) {
            String key = getNameOf(value);
            V storedValue = data.get(key);
            if (! value.equals(storedValue)) return false;
            return (data.remove(key) != null);
        }

        public void clear() {
            values.clear();
        }

    }

    private final Map<String, V> data;
    private final ValueCollection values;

    public NamedMap(Map<String, V> data) {
        this.data = data;
        this.values = new ValueCollection();
        validateBackingMap(data);
    }
    public NamedMap() {
        this(new HashMap<String, V>());
    }

    protected void validateBackingMap(Map<String, V> map) {
        for (Map.Entry<String, V> ent : map.entrySet()) {
            // Intentionally permitting NPE-s.
            if (! ent.getKey().equals(ent.getValue().getName()))
                throw new IllegalArgumentException(
                    "Invalid pair in NamedMap backing data");
        }
    }

    public Set<String> keySet() {
        return data.keySet();
    }

    public Collection<V> values() {
        return values;
    }

    public Set<Entry<String, V>> entrySet() {
        // entrySet()'s return value is supposed not to support addition, so
        // this should be fine.
        return data.entrySet();
    }

    public boolean containsKey(Object key) {
        return data.containsKey(key);
    }

    public boolean containsValue(Object value) {
        String key = getNameOf(value);
        return containsKey(key) && value.equals(get(key));
    }

    public V get(Object key) {
        return data.get(key);
    }

    public V put(String key, V value) {
        // Intentionally permitting NPE-s.
        if (! key.equals(value.getName()))
            throw new IllegalArgumentException("Cannot insert pair " + key +
                ":" + value + " into NamedMap");
        return data.put(key, value);
    }

    public V remove(Object key) {
        return data.remove(key);
    }

    private static String getNameOf(Object value) {
        return getNameOf((NamedValue) value);
    }
    private static String getNameOf(NamedValue value) {
        String key = value.getName();
        if (key == null)
            throw new NullPointerException(
                "NamedValue name may not be null");
        return key;
    }

}
