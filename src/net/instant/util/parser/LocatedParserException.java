package net.instant.util.parser;

import net.instant.api.parser.TextLocation;

public class LocatedParserException extends ParserException {

    private final TextLocation position;

    public LocatedParserException(TextLocation pos) {
        super();
        position = pos;
    }
    public LocatedParserException(TextLocation pos, String message) {
        super(message);
        position = pos;
    }
    public LocatedParserException(TextLocation pos, Throwable cause) {
        super(cause);
        position = pos;
    }
    public LocatedParserException(TextLocation pos, String message,
                                  Throwable cause) {
        super(message, cause);
        position = pos;
    }

    public TextLocation getPosition() {
        return position;
    }

}
