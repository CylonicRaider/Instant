package net.instant.api.parser;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A convenience implementation of Mapper decomposing the input parse tree.
 * This class aims to allow performing several mapping operations that need to
 * process sub-parse-trees with as little subclass code as possible:
 * - Mapping fixed record-like structures: The Iterator implementation of
 *   Provider (and, in particular, its mapNext() method) allows easily
 *   processing sub-trees; length checks (both for missing and for superfluous
 *   sub-trees) are performed automatically.
 * - Mapping leaf nodes (which should not have subtrees): This arises as a
 *   special case from above.
 * - Mapping lists of things: The Iterable implementation of Provider allows
 *   iterating over the sub-trees of a parse tree (possibly omitting a fixed
 *   prefix).
 */
public abstract class RecordMapper<T> implements Mapper<T> {

    /**
     * Marker exception thrown by Provider.next().
     */
    private static class NoSuchTreeException extends NoSuchElementException {

        public NoSuchTreeException() {
            super();
        }
        public NoSuchTreeException(String message) {
            super(message);
        }

    }

    /**
     * This class combines multiple functionalities and serves as the
     * parameter object for mapInner().
     * - A getter to access the ParseTree being processed is provided.
     * - The sub-trees of the tree can be accessed individually via the
     *   Iterator interface, as well as via the mapNext() method.
     * - The remaining sub-trees can be processed using for-each loops via the
     *   Iterable interface.
     */
    public static class Provider implements Iterable<Parser.ParseTree>,
                                            Iterator<Parser.ParseTree> {

        private final Parser.ParseTree tree;
        private final Iterator<Parser.ParseTree> iterator;

        /** Construct a new Provider processing the given parse tree. */
        public Provider(Parser.ParseTree tree) {
            this.tree = tree;
            this.iterator = tree.getChildren().iterator();
        }

        /** Return this Provider's inner parse tree. */
        public Parser.ParseTree getParseTree() {
            return tree;
        }

        /** Return this Provider (for for-each loop use). */
        public Iterator<Parser.ParseTree> iterator() {
            return this;
        }

        /** Check whether another sub-tree is available. */
        public boolean hasNext() {
            return iterator.hasNext();
        }

        /** Return the next sub-tree, or throw a NoSuchElementException. */
        public Parser.ParseTree next() {
            try {
                return iterator.next();
            } catch (NoSuchElementException exc) {
                NoSuchTreeException wrapper = new NoSuchTreeException(
                    exc.getMessage());
                wrapper.initCause(exc);
                throw wrapper;
            }
        }

        /** Element removal is not supported. */
        public void remove() {
            throw new UnsupportedOperationException(
                "May not remove from ParseTree provider");
        }

        /** Convenience wrapper for mapper.map(next()). */
        public <U> U mapNext(Mapper<U> mapper) throws MappingException {
            return mapper.map(next());
        }

    }

    /**
     * Map the given parse tree to an object or throw an exception.
     * After constructing a Provider, this delegates to mapInner(), converts
     * exceptions thrown by Provider.next() to MappingException-s, performs a
     * final length check (to ensure that no sub-trees have been missed), and
     * returns the value obtained from mapInner().
     */
    public T map(Parser.ParseTree tree) throws MappingException {
        Provider p = new Provider(tree);
        T ret;
        try {
            ret = mapInner(p);
        } catch (NoSuchTreeException exc) {
            throw new MappingException(exc.getMessage(), exc);
        }
        if (p.hasNext())
            throw new MappingException("Parse tree has too many children");
        return ret;
    }

    /**
     * Primary method for subclasses.
     */
    protected abstract T mapInner(Provider p) throws MappingException;

}
