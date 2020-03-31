package net.instant.api.parser;

import java.util.Set;

/**
 * A read-only view of a Grammar.
 * The Set-s returned by the methods in this interface are unmodifiable.
 */
public interface GrammarView {

    /**
     * The start symbol of the GrammarView.
     */
    Grammar.NonterminalSymbol getStartSymbol();

    /**
     * The names of all productions in this GrammarView.
     */
    Set<String> getProductionNames();

    /**
     * The productions of this GrammarView with the given name, or null (or
     * an empty set) if none.
     */
    Set<Grammar.Production> getProductions(String name);

}
