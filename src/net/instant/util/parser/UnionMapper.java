package net.instant.util.parser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class UnionMapper<T> implements Mapper<T> {

    private final Map<String, Mapper<? extends T>> registry;

    public UnionMapper(Map<String, Mapper<? extends T>> registry) {
        if (registry == null)
            throw new NullPointerException("registry may not be null");
        this.registry =
            new LinkedHashMap<String, Mapper<? extends T>>(registry);
    }
    public UnionMapper() {
        registry = new LinkedHashMap<String, Mapper<? extends T>>();
    }

    public Map<String, Mapper<? extends T>> getChildren() {
        return Collections.unmodifiableMap(registry);
    }

    public boolean contains(String name) {
        return registry.containsKey(name);
    }
    public void add(String name, Mapper<? extends T> child) {
        registry.put(name, child);
    }
    public void remove(String name) {
        registry.remove(name);
    }

    public boolean canMap(ParseTree pt) {
        return contains(pt.getName());
    }

    public T map(ParseTree pt) throws MappingException {
        Mapper<? extends T> child = registry.get(pt.getName());
        if (child == null)
            throw new MappingException("Cannot map node type " +
                pt.getName());
        return child.map(pt);
    }

}
