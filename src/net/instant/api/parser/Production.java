package net.instant.api.parser;

import java.util.List;
import net.instant.api.NamedValue;

/**
 * An (immutable) element of a Grammar.
 * A Production has a name that relates it to same-named Production-s and a
 * list of Symbol-s that define what the Production matches.
 * It matches the concatenation of the languages of its symbols (in their
 * respective order); e.g., if the symbols A, B, and C match the strings
 * "Hello", " ", and "World", then a production containing (only) A, B, and C
 * matches the string "Hello World".
 */
public interface Production extends NamedValue {

    /**
     * The symbols of this Production.
     */
    List<Symbol> getSymbols();

}
