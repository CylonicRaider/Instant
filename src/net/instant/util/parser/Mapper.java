package net.instant.util.parser;

public interface Mapper<T> {

    T map(Parser.ParseTree pt) throws MappingException;

}
