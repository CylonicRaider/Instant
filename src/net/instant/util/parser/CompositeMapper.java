package net.instant.util.parser;

import java.util.Collections;
import java.util.List;

public abstract class CompositeMapper<C, T>
        extends BaseCompositeMapper<C, T> {

    private Mapper<C> childMapper;

    public CompositeMapper(Mapper<C> childMapper) {
        this.childMapper = childMapper;
    }
    public CompositeMapper() {
        this(null);
    }

    public Mapper<C> getChildMapper() {
        return childMapper;
    }
    public void setChildMapper(Mapper<C> cm) {
        childMapper = cm;
    }

    protected C mapChild(ParseTree pt) throws MappingException {
        return childMapper.map(pt);
    }

    public static <C, T> CompositeMapper<C, T> of(
            final NodeMapper<C, T> reduce, Mapper<C> map) {
        return new CompositeMapper<C, T>(map) {
            protected T mapInner(ParseTree pt, List<C> children)
                    throws MappingException {
                return reduce.map(pt, children);
            }
        };
    }

    public static <T> CompositeMapper<T, List<T>> aggregate(Mapper<T> nested,
            final boolean makeImmutable) {
        return new CompositeMapper<T, List<T>>(nested) {
            protected List<T> mapInner(ParseTree pt,
                                       List<T> children) {
                if (makeImmutable)
                    children = Collections.unmodifiableList(children);
                return children;
            }
        };
    }
    public static <T> CompositeMapper<T, List<T>> aggregate(
            Mapper<T> nested) {
        return aggregate(nested, false);
    }

    public static <T> CompositeMapper<T, T> passthrough(Mapper<T> nested) {
        return new CompositeMapper<T, T>(nested) {
            public T map(ParseTree pt) throws MappingException {
                if (pt.childCount() != 1)
                    throw new MappingException("Expected one subtree, got " +
                        pt.childCount());
                return super.map(pt);
            }

            protected T mapInner(ParseTree pt, List<T> children) {
                return children.get(0);
            }
        };
    }

}
