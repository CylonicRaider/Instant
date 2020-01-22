package net.instant.util.parser;

public abstract class LeafMapper<T> implements Mapper<T> {

    public interface NodeMapper<T> {

        T map(Lexer.Token tok);

    }

    public T map(Parser.ParseTree pt) {
        if (pt.childCount() > 0)
            throw new IllegalArgumentException("Cannot map parse tree to " +
                "object: Expected leaf, got non-leaf");
        return mapInner(pt.getToken());
    }

    protected abstract T mapInner(Lexer.Token tok);

    public static <T> LeafMapper<T> of(final NodeMapper<T> wrap) {
        return new LeafMapper<T>() {
            protected T mapInner(Lexer.Token tok) {
                return wrap.map(tok);
            }
        };
    }

}
