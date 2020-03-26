package net.instant.api.parser;

/**
 * A location inside a multi-line text stream.
 * The location consists of line and column numbers as well as a character
 * index; see the method descriptions for more details.
 */
public interface TextLocation {

    /**
     * The 1-based line index.
     */
    long getLine();

    /**
     * The 1-based column index.
     */
    long getColumn();

    /**
     * The 0-based character index. Characters are Java characters, i.e.
     * UTF-16 code units.
     */
    long getCharacterIndex();

}
