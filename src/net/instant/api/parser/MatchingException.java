package net.instant.api.parser;

/**
 * Exception thrown when a TokenSource cannot satisfy a request for a token.
 */
public class MatchingException extends LocatedParserException {

    public MatchingException(TextLocation pos) {
        super(pos);
    }
    public MatchingException(TextLocation pos, String message) {
        super(pos, message);
    }
    public MatchingException(TextLocation pos, Throwable cause) {
        super(pos, cause);
    }
    public MatchingException(TextLocation pos, String message,
                             Throwable cause) {
        super(pos, message, cause);
    }

}
