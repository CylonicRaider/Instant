package net.instant.api.parser;

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

}
