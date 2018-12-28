package net.instant.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class WeakIndex<K, V> {

    private final Map<K, WeakReference<V>> wrapped;
    private final ReferenceQueue<V> queue;
    private final List<V> toRemove;

    public WeakIndex() {
        this.wrapped = new WeakHashMap<K, WeakReference<V>>();
        this.queue = new ReferenceQueue<V>();
        this.toRemove = new ArrayList<V>();
    }

    public boolean equals(Object o) {
        if (! (o instanceof WeakIndex)) return false;
        return wrapped.equals(((WeakIndex) o).wrapped);
    }

    public int hashCode() {
        return wrapped.hashCode();
    }

    public int size() {
        pumpReferences();
        return wrapped.size();
    }

    public boolean isEmpty() {
        pumpReferences();
        return wrapped.isEmpty();
    }

    public V get(Object key) {
        pumpReferences();
        return resolveRef(wrapped.get(key));
    }

    public V put(K key, V value) {
        pumpReferences();
        return resolveRef(wrapped.put(key,
                                      new WeakReference<V>(value, queue)));
    }

    public V remove(Object key) {
        pumpReferences();
        return resolveRef(wrapped.remove(key));
    }

    public void clear() {
        pumpReferences();
        wrapped.clear();
    }

    protected void pumpReferences() {
        Reference<? extends V> ref = queue.poll();
        if (ref == null) return;
        synchronized (toRemove) {
            while (ref != null) {
                V obj = ref.get();
                if (obj != null) toRemove.add(obj);
                ref = queue.poll();
            }
            wrapped.values().removeAll(toRemove);
            toRemove.clear();
        }
    }

    private static <T> T resolveRef(WeakReference<T> ref) {
        return (ref == null) ? null : ref.get();
    }

}
