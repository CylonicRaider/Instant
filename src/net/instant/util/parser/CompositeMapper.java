package net.instant.util.parser;

import java.util.List;

public abstract class CompositeMapper<C, T>
        extends BaseCompositeMapper<C, T> {

    private final Mapper<C> childMapper;

    public CompositeMapper(Mapper<C> childMapper) {
        this.childMapper = childMapper;
    }

    protected Mapper<C> getChildMapper() {
        return childMapper;
    }

    protected C mapChild(Parser.ParseTree pt) {
        return childMapper.map(pt);
    }

    public static <C, T> CompositeMapper<C, T> of(
            final NodeMapper<C, T> reduce, Mapper<C> map) {
        return new CompositeMapper<C, T>(map) {
            protected T mapInner(Parser.ParseTree pt, List<C> children) {
                return reduce.map(pt, children);
            }
        };
    }

}
