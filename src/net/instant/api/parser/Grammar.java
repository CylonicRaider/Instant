package net.instant.api.parser;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A (context-free) Grammar defines a formal language, i.e. a set of strings
 * that it "matches".
 * The grammar consists of productions, which, in turn, consist of symbols;
 * it also carries a special start symbol. The grammar matches those strings
 * that are matched by its start symbol, which, in turn, refers (directly or
 * transitively) to productions in the grammar, whose matching behavior is
 * defined by the symbols contained inside *them*. See the Production and
 * Symbol interfaces for details on their matching behavior.
 * This provides a mutable interface, along with factory methods for creating
 * various parts of a Grammar.
 */
public interface Grammar extends GrammarView {

    /**
     * Create a NonterminalSymbol referring to the given name.
     */
    NonterminalSymbol createNonterminal(String reference, int flags);

    /**
     * Create a TerminalSymbol matching (only) the given string.
     */
    TerminalSymbol createTerminal(String content, int flags);

    /**
     * Create a TerminalSymbol matching the language matched by the given
     * Pattern.
     */
    TerminalSymbol createTerminal(Pattern pattern, int flags);

    /**
     * Create a Production with the given name and symbols.
     */
    Production createProduction(String name, List<Symbol> symbols);

    /**
     * Add the given production to the Grammar.
     */
    void addProduction(Production prod);

    /**
     * Remove the given production from the Grammar.
     */
    void removeProduction(Production prod);

}
