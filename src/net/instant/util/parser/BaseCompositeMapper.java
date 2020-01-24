package net.instant.util.parser;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseCompositeMapper<C, T> implements Mapper<T> {

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

}
