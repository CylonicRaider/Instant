package net.instant.util.parser;

public interface Symbol {

    /* Do not generate an own parsing tree node for this symbol. */
    int SYM_INLINE = 1;
    /* Discard any parsing tree nodes stemming from this symbol (may be
     * overridden on a per-parser basis). */
    int SYM_DISCARD = 2;
    /* Optionally skip this symbol (regular expression x?).
     * If the symbol is not matched, no parse tree is generated for it. */
    int SYM_OPTIONAL = 4;
    /* Permit repetitions of this symbol (regular expression x+).
     * Multiple matches generate adjacent parse subtrees. Combine with
     * SYM_OPTIONAL to permit any amount of repetitions (regular expression
     * x*). */
    int SYM_REPEAT = 8;

    /* All known flags combined. */
    int SYM_ALL = SYM_INLINE | SYM_DISCARD | SYM_OPTIONAL | SYM_REPEAT;

    int getFlags();

    Symbol withFlags(int newFlags);

}
