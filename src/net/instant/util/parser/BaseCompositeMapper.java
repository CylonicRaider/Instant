package net.instant.util.parser;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseCompositeMapper<C, T> implements Mapper<T> {

    public interface NodeMapper<C, T> {

        T map(ParseTree pt, List<C> children) throws MappingException;

    }

    public T map(ParseTree pt) throws MappingException {
        List<C> children = new ArrayList<C>(pt.childCount());
        for (ParseTree t : pt.getChildren()) {
            children.add(mapChild(t));
        }
        return mapInner(pt, children);
    }

    protected abstract T mapInner(ParseTree pt, List<C> children)
        throws MappingException;

    protected abstract C mapChild(ParseTree pt)
        throws MappingException;

}
