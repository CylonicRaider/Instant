package net.instant.api.parser;

/**
 * Generic interface for mapping parse trees to (other) objects.
 */
public interface Mapper<T> {

    /**
     * Map the given parse tree to an object or throw an exception.
     */
    T map(Parser.ParseTree pt) throws MappingException;

}
