package net.instant.util.parser;

import java.util.ArrayList;
import java.util.List;

public abstract class CompositeMapper<C, T> implements Mapper<T> {

    public interface NodeMapper<C, T> {

        T map(Parser.ParseTree pt, List<C> children);

    }

    public T map(Parser.ParseTree pt) {
        List<C> children = new ArrayList<C>(pt.childCount());
        for (Parser.ParseTree t : pt.getChildren()) {
            children.add(mapChild(t));
        }
        return mapInner(pt, children);
    }

    protected abstract T mapInner(Parser.ParseTree pt, List<C> children);

    protected abstract C mapChild(Parser.ParseTree pt);

    public static <C, T> CompositeMapper<C, T> of(
            final NodeMapper<C, T> reduce, final Mapper<C> map) {
        return new CompositeMapper<C, T>() {

            protected T mapInner(Parser.ParseTree pt, List<C> children) {
                return reduce.map(pt, children);
            }

            protected C mapChild(Parser.ParseTree pt) {
                return map.map(pt);
            }

        };
    }

}
