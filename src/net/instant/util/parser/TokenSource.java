package net.instant.util.parser;

import java.io.Closeable;
import java.util.Map;
import net.instant.util.LineColumnReader;

public interface TokenSource extends Closeable {

    interface Selection {

        Map<String, TokenPattern> getPatterns();

        boolean isCompatibleWith(Selection other);

        boolean contains(Token tok);

    }

    enum MatchStatus { OK, NO_MATCH, EOI }

    class MatchingException extends LocatedParserException {

        public MatchingException(LineColumnReader.Coordinates pos) {
            super(pos);
        }
        public MatchingException(LineColumnReader.Coordinates pos,
                                String message) {
            super(pos, message);
        }
        public MatchingException(LineColumnReader.Coordinates pos,
                                Throwable cause) {
            super(pos, cause);
        }
        public MatchingException(LineColumnReader.Coordinates pos,
                                String message, Throwable cause) {
            super(pos, message, cause);
        }

    }

    void setSelection(Selection sel);

    LineColumnReader.Coordinates getCurrentPosition();

    Token getCurrentToken();

    MatchStatus peek(boolean required) throws MatchingException;

    Token next() throws MatchingException;

}
