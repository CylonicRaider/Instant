package net.instant.util.parser;

import net.instant.util.LineColumnReader;

public class LexerException extends Exception {

    private final LineColumnReader.Coordinates position;

    public LexerException(LineColumnReader.Coordinates pos) {
        super();
        position = pos;
    }
    public LexerException(LineColumnReader.Coordinates pos, String message) {
        super(message);
        position = pos;
    }
    public LexerException(LineColumnReader.Coordinates pos, Throwable cause) {
        super(cause);
        position = pos;
    }
    public LexerException(LineColumnReader.Coordinates pos, String message,
                          Throwable cause) {
        super(message, cause);
        position = pos;
    }

    public LineColumnReader.Coordinates getPosition() {
        return position;
    }

}
