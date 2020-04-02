package net.instant.util.parser;

import java.util.ArrayList;
import java.util.List;
import net.instant.api.parser.Mapper;
import net.instant.api.parser.MappingException;
import net.instant.api.parser.Parser;

public abstract class BaseCompositeMapper<C, T> implements Mapper<T> {

    public interface NodeMapper<C, T> {

        T map(Parser.ParseTree pt, List<C> children) throws MappingException;

    }

    public T map(Parser.ParseTree pt) throws MappingException {
        List<Parser.ParseTree> childTrees = pt.getChildren();
        List<C> children = new ArrayList<C>(childTrees.size());
        for (Parser.ParseTree t : childTrees) {
            children.add(mapChild(t));
        }
        return mapInner(pt, children);
    }

    protected abstract T mapInner(Parser.ParseTree pt, List<C> children)
        throws MappingException;

    protected abstract C mapChild(Parser.ParseTree pt)
        throws MappingException;

}
