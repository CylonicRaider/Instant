package net.instant.util.parser;

import net.instant.util.LineColumnReader;

public class LocatedParserException extends BaseParserException {

    private final LineColumnReader.Coordinates position;

    public LocatedParserException(LineColumnReader.Coordinates pos) {
        super();
        position = pos;
    }
    public LocatedParserException(LineColumnReader.Coordinates pos,
                                  String message) {
        super(message);
        position = pos;
    }
    public LocatedParserException(LineColumnReader.Coordinates pos,
                                  Throwable cause) {
        super(cause);
        position = pos;
    }
    public LocatedParserException(LineColumnReader.Coordinates pos,
                                  String message, Throwable cause) {
        super(message, cause);
        position = pos;
    }

    public LineColumnReader.Coordinates getPosition() {
        return position;
    }

}
