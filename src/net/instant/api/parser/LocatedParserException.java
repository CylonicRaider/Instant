package net.instant.api.parser;

/**
 * A generic ParserException that has an associated TextLocation.
 */
public class LocatedParserException extends ParserException {

    private final TextLocation location;

    public LocatedParserException(TextLocation pos) {
        super();
        location = pos;
    }
    public LocatedParserException(TextLocation pos, String message) {
        super(message);
        location = pos;
    }
    public LocatedParserException(TextLocation pos, Throwable cause) {
        super(cause);
        location = pos;
    }
    public LocatedParserException(TextLocation pos, String message,
                                  Throwable cause) {
        super(message, cause);
        location = pos;
    }

    public TextLocation getLocation() {
        return location;
    }

}
