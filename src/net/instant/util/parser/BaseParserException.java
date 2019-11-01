package net.instant.util.parser;

public class BaseParserException extends Exception {

    public BaseParserException() {
        super();
    }
    public BaseParserException(String message) {
        super(message);
    }
    public BaseParserException(Throwable cause) {
        super(cause);
    }
    public BaseParserException(String message, Throwable cause) {
        super(message, cause);
    }

}
