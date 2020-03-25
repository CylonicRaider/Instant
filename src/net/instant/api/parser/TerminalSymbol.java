package net.instant.api.parser;

import java.util.regex.Pattern;

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
