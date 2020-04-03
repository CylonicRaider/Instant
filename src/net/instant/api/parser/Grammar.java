package net.instant.api.parser;

import java.util.List;
import java.util.regex.Pattern;
import net.instant.api.NamedValue;

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
     * An (immutable) element of a Production.
     * A Symbol can be a TerminalSymbol, which matches the strings matched by
     * some regular expression, or a NonterminalSymbol, which matches the
     * languages matched by productions with a corresponding name; see the
     * aforementioned interfaces for more details.
     * Clases implementing Symbol should implement the equals() (and
     * hashCode()) methods such that symbols created by passing equal
     * arguments to createNonterminal() and createTerminal() are equal, and
     * symbols created by passing unequal arguments to the factory methods
     * are not equal. Pattern-s are considered equal if their pattern strings
     * and flags are equal.
     */
    public interface Symbol {

        /**
         * Do not generate an own parsing tree node for this symbol.
         */
        int SYM_INLINE = 1;

        /**
         * Discard any parsing tree nodes stemming from this symbol (may be
         * overridden on a per-parser basis).
         */
        int SYM_DISCARD = 2;

        /**
         * Optionally skip this symbol (regular expression x?).
         * If the symbol is not matched, no parse tree is generated for it.
         */
        int SYM_OPTIONAL = 4;

        /**
         * Permit repetitions of this symbol (regular expression x+).
         * Multiple matches generate adjacent parse subtrees. Combine with
         * SYM_OPTIONAL to permit any amount of repetitions (regular
         * expression x*).
         */
        int SYM_REPEAT = 8;

        /**
         * All known Symbol flags combined.
         */
        int SYM_ALL = SYM_INLINE | SYM_DISCARD | SYM_OPTIONAL | SYM_REPEAT;

        /**
         * Retrieve the flags of this symbol.
         */
        int getFlags();

        /**
         * Return how strongly this Symbol should be preferred when producing
         * tokens.
         * Symbols with greater match ranks should be preferred. Typically,
         * this returns a class-specific constant that may be overridden by
         * subclasses.
         */
        int getMatchRank();

        /**
         * Return a copy of this Symbol modified to have the given flags.
         */
        Symbol withFlags(int newFlags);

    }

    /**
     * A Symbol whose language is defined by a set of productions.
     * The symbol matches a string if any of the production with the name the
     * symbol refers to match the string. E.g., if the symbol's reference is
     * "A", and the grammar contains two productions with that name that
     * match, respectively, the strings "Hello" and "World", then the symbol
     * matches either "Hello" or "World".
     */
    public interface NonterminalSymbol extends Symbol {

        /**
         * The name of the production(s) this symbol references.
         */
        String getReference();

    }

    /**
     * A Symbol that matches literal text (defined by a regular expression).
     * E.g., if a TerminalSymbol has a pattern of /Hello [Ww]orld/, then it
     * matches either the string "Hello World" or "Hello world".
     */
    public interface TerminalSymbol extends Symbol {

        /**
         * A regular expression defining what strings this symbol matches.
         */
        Pattern getPattern();

    }

    /**
     * An (immutable) element of a Grammar.
     * A Production has a name that relates it to same-named Production-s and
     * a list of Symbol-s that define what the Production matches.
     * It matches the concatenation of the languages of its symbols (in their
     * respective order); e.g., if the symbols A, B, and C match the strings
     * "Hello", " ", and "World", then a production containing (only) A, B,
     * and C matches the string "Hello World".
     * Classes implementing Production should implement equals() (and
     * hashCode()) such that two productions are equal if-and-only-if their
     * names and their symbol lists are equal.
     */
    public interface Production extends NamedValue {

        /**
         * The symbols of this Production.
         */
        List<Symbol> getSymbols();

    }

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
