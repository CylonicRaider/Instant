package net.instant.api.parser;

/**
 * A Symbol whose language is defined by a set of productions.
 * The symbol matches a string if any of the production with the name the
 * symbol refers to match the string. E.g., if the symbol's reference is
 * "A", and the grammar contains two productions with that name that match,
 * respectively, the strings "Hello" and "World", then the symbol matches
 * either "Hello" or "World".
 */
public interface NonterminalSymbol extends Symbol {

    /**
     * The name of the production(s) this symbol references.
     */
    String getReference();

}
