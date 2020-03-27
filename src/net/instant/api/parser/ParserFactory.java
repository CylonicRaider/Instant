package net.instant.api.parser;

import java.io.Reader;

/**
 * Entry-point interface for accessing various parts of the parser API.
 */
public interface ParserFactory {

    /**
     * Create a new empty grammar.
     */
    Grammar createGrammar();

    /**
     * Create a new grammar containing the productions from the given
     * GrammarView.
     */
    Grammar createGrammar(GrammarView copyFrom);

    /**
     * Convert the given grammar into a form suitable for the quick creation
     * of parsers.
     * This validates the grammar and creates internal data structures that
     * are shared among parsers.
     */
    CompiledGrammar compile(GrammarView grammar)
        throws InvalidGrammarException;

    /**
     * Create a default TokenSource extracting tokens from the given Reader.
     */
    TokenSource createTokenSource(Reader input);

    /**
     * Retrieve the grammar used by parseGrammar().
     */
    CompiledGrammar getMetaGrammar();

    /**
     * Retrieve the parse-tree-to-object mapper used by parseGrammar().
     */
    Mapper<Grammar> getGrammarMapper();

    /**
     * Parse a grammar definition from the given Reader and convert it to a
     * Grammar instance.
     * This is a convenience method combining the effects of
     * createTokenSource(), the grammar-parsing grammar from getMetaGrammar(),
     * and the Mapper returned by getGrammarMapper() (or doing something
     * equivalent).
     */
    Grammar parseGrammar(Reader input) throws ParsingException;

}
