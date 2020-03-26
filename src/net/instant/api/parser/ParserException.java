package net.instant.api.parser;

/**
 * Generic superclass for checked parser module exceptions.
 * Not to be confused with the specific ParsingException.
 */
public class ParserException extends Exception {

    public ParserException() {
        super();
    }
    public ParserException(String message) {
        super(message);
    }
    public ParserException(Throwable cause) {
        super(cause);
    }
    public ParserException(String message, Throwable cause) {
        super(message, cause);
    }

}
