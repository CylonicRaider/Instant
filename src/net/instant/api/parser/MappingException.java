package net.instant.api.parser;

/**
 * Exception thrown on object mapping failures.
 */
public class MappingException extends ParserException {

    public MappingException() {
        super();
    }
    public MappingException(String message) {
        super(message);
    }
    public MappingException(Throwable cause) {
        super(cause);
    }
    public MappingException(String message, Throwable cause) {
        super(message, cause);
    }

}
