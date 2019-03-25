package net.instant.util.argparse;

public class ValueMissingException extends ParsingException {

    public ValueMissingException(String message) {
        super(message);
    }
    public ValueMissingException(String message, String source) {
        super(message, source);
    }
    public ValueMissingException(String message, Throwable cause) {
        super(message, cause);
    }
    public ValueMissingException(String message, String source,
                                 Throwable cause) {
        super(message, source, cause);
    }
    public ValueMissingException(ValueMissingException cause,
                                 String newSource) {
        super(cause, newSource);
    }

}
