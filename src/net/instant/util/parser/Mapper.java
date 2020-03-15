package net.instant.util.parser;

public interface Mapper<T> {

    T map(ParseTree pt) throws MappingException;

}
