package net.instant.api.parser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Mapper dispatching to other mappers depending on the parse tree name.
 * A UnionMapper maintains a set of *children*, which are idenfified by
 * String names. The map() method picks a child whose key matches the name of
 * the parse tree passed to map() and delegates further processing to that
 * child.
 * Children are registered via the add() method, or by directly accessing the
 * collection of children via getChildren().
 */
public class UnionMapper<T> implements Mapper<T> {

    private final Map<String, Mapper<? extends T>> children;

    /**
     * Create a new UnionMapper with no children.
     */
    public UnionMapper() {
        this.children = new LinkedHashMap<String, Mapper<? extends T>>();
    }

    /**
     * Return the children of this UnionMapper.
     */
    public Map<String, Mapper<? extends T>> getChildren() {
        return children;
    }

    /**
     * Convenience method to register a new child.
     * Equivalent to getChildren().put(name, child).
     */
    public void add(String name, Mapper<? extends T> child) {
        getChildren().put(name, child);
    }

    /**
     * Test whether the given parse tree can be processed by this UnionMapper.
     * If this method returns false for a given tree, then map() will fail on
     * it (changes of the internal data structure notwithstanding); the
     * converse need not be true.
     */
    public boolean canMap(Parser.ParseTree tree) {
        return getChildren().containsKey(tree.getName());
    }

    /**
     * Map the given parse tree to an object or throw an exception.
     * This locates the child whose key is the name of the given parse tree
     * and delegates to the child's map() method. If there is no matching
     * child, this throws a MappingException.
     */
    public T map(Parser.ParseTree tree) throws MappingException {
        Mapper<? extends T> child = getChildren().get(tree.getName());
        if (child == null)
            throw new MappingException("Cannot map parse tree node type " +
                tree.getName());
        return child.map(tree);
    }

}
