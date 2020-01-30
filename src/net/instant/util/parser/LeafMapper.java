package net.instant.util.parser;

public abstract class LeafMapper<T> implements Mapper<T> {

    private static LeafMapper<String> STRING = new LeafMapper<String>() {
        protected String mapInner(Parser.ParseTree pt) {
            return pt.getName();
        }
    };

    public T map(Parser.ParseTree pt) {
        if (pt.childCount() > 0)
            throw new IllegalArgumentException("Cannot map parse tree to " +
                "object: Expected leaf, got non-leaf");
        return mapInner(pt);
    }

    protected abstract T mapInner(Parser.ParseTree pt);

    public static <T> LeafMapper<T> of(final Mapper<T> wrapped) {
        return new LeafMapper<T>() {
            protected T mapInner(Parser.ParseTree pt) {
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
