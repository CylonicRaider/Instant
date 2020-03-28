package net.instant.api.parser;

/**
 * An (immutable) element of a Production.
 * A Symbol can be a TerminalSymbol, which matches the strings matched by some
 * regular expression, or a NonterminalSymbol, which matches the languages
 * matched by productions with a corresponding name; see the aforementioned
 * interfaces for more details. The Grammar interface provides factory methods
 * for creating Symbol-s with different meanings.
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
     * SYM_OPTIONAL to permit any amount of repetitions (regular expression
     * x*).
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
     * Symbols with greater match ranks should be preferred. Typically, this
     * returns a class-specific constant that may be overridden by subclasses.
     */
    int getMatchRank();

    /**
     * Return a copy of this Symbol modified to have the given flags.
     */
    Symbol withFlags(int newFlags);

}
