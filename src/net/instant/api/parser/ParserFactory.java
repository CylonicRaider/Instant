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

}
