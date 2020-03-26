package net.instant.api.parser;

/**
 * Exception thrown on Parser errors.
 * Not to be confused with the generic ParserException.
 */
public class ParsingException extends LocatedParserException {

    public ParsingException(TextLocation pos) {
        super(pos);
    }
    public ParsingException(TextLocation pos, String message) {
        super(pos, message);
    }
    public ParsingException(TextLocation pos, Throwable cause) {
        super(pos, cause);
    }
    public ParsingException(TextLocation pos, String message,
                            Throwable cause) {
        super(pos, message, cause);
    }

}
