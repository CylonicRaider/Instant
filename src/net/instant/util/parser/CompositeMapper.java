package net.instant.util.parser;

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
