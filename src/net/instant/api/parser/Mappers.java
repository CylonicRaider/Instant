package net.instant.api.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Various convenience Mapper implementations.
 */
public final class Mappers {

    private static final Mapper<Parser.ParseTree> IDENTITY =
        new Mapper<Parser.ParseTree>() {
            public Parser.ParseTree map(Parser.ParseTree tree) {
                return tree;
            }
        };

    private static final RecordMapper<String> NAME =
        new RecordMapper<String>() {
            protected String mapInner(Provider p) {
                return p.getParseTree().getName();
            }
        };

    private static final RecordMapper<String> CONTENT =
        new RecordMapper<String>() {
            protected String mapInner(Provider p) throws MappingException {
                if (p.getParseTree().getToken() == null)
                    throw new MappingException("Parse tree " +
                        p.getParseTree().getName() + " should have a token");
                return p.getParseTree().getToken().getContent();
            }
        };

    /* Prevent construction */
    private Mappers() {}

    /**
     * A Mapper that merely returns the ParseTree passed to it.
     */
    public static Mapper<Parser.ParseTree> identity() {
        return IDENTITY;
    }

    /**
     * A Mapper that maps leaf parse trees to their names.
     * If the parse tree passed to the mapper has children, it is rejected
     * with a MappingException.
     */
    public static Mapper<String> name() {
        return NAME;
    }

    /**
     * A Mapper that maps leaf parse trees to their tokens' contents.
     * If a parse tree passed to the mapper has children or no token, it is
     * rejected with a MappingException.
     */
    public static Mapper<String> content() {
        return CONTENT;
    }

    /**
     * A Mapper that always returns the given constant value.
     */
    public static <T> Mapper<T> constant(final T value) {
        return new Mapper<T>() {
            public T map(Parser.ParseTree tree) {
                return value;
            }
        };
    }

    /**
     * A Mapper that maps the children of a parse trees using another mapper
     * and aggregates the results into a list.
     * The returned list is modifiable.
     */
    public static <T> Mapper<List<T>> aggregate(final Mapper<T> element) {
        return new RecordMapper<List<T>>() {
            protected List<T> mapInner(Provider p) throws MappingException {
                List<T> ret = new ArrayList<T>(
                    p.getParseTree().getChildren().size());
                while (p.hasNext()) ret.add(p.mapNext(element));
                return ret;
            }
        };
    }

    /**
     * A Mapper that concatenates lists produced from children of the
     * ParseTree it is applied to.
     * The returned list is modifiable.
     */
    public static <T> Mapper<List<T>> join(final Mapper<List<T>> part) {
        return new RecordMapper<List<T>>() {
            protected List<T> mapInner(Provider p) throws MappingException {
                List<T> ret = new ArrayList<T>();
                while (p.hasNext()) ret.addAll(p.mapNext(part));
                return ret;
            }
        };
    }

    /**
     * A Mapper that extracts a single child from a ParseTree and applies
     * another Mapper to that.
     * If the ParseTree does not have exactly one child, it is rejected with
     * a MappingException.
     */
    public static <T> Mapper<T> unwrap(final Mapper<T> inner) {
        return new RecordMapper<T>() {
            protected T mapInner(Provider p) throws MappingException {
                return p.mapNext(inner);
            }
        };
    }

}
