package net.instant.api.parser;

/**
 * Exception thrown during grammar validation.
 */
public class InvalidGrammarException extends ParserException {

    public InvalidGrammarException() {
        super();
    }
    public InvalidGrammarException(String message) {
        super(message);
    }
    public InvalidGrammarException(Throwable cause) {
        super(cause);
    }
    public InvalidGrammarException(String message, Throwable cause) {
        super(message, cause);
    }

}
