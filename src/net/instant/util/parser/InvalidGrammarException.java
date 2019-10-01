package net.instant.util.parser;

public class InvalidGrammarException extends Exception {

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
