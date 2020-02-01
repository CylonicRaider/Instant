package net.instant.util.parser;

public abstract class LeafMapper<T> implements Mapper<T> {

    private static LeafMapper<String> STRING = new LeafMapper<String>() {
        protected String mapInner(Parser.ParseTree pt) {
            return pt.getName();
        }
    };

    public T map(Parser.ParseTree pt) throws MappingException {
        if (pt.childCount() > 0)
            throw new MappingException(
                "Expected leaf node, got non-leaf node");
        return mapInner(pt);
    }

    protected abstract T mapInner(Parser.ParseTree pt)
        throws MappingException;

    public static <T> LeafMapper<T> of(final Mapper<T> wrapped) {
        return new LeafMapper<T>() {
            protected T mapInner(Parser.ParseTree pt)
                    throws MappingException {
                return wrapped.map(pt);
            }
        };
    }

    public static <T> LeafMapper<T> constant(final T value) {
        return new LeafMapper<T>() {
            protected T mapInner(Parser.ParseTree pt) {
                return value;
            }
        };
    }

    public static LeafMapper<String> string() {
        return STRING;
    }

}
