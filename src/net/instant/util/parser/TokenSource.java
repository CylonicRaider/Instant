package net.instant.util.parser;

import java.io.Closeable;
import java.util.Map;
import net.instant.api.parser.TextLocation;

public interface TokenSource extends Closeable {

    interface Selection {

        Map<String, Lexer.TokenPattern> getPatterns();

        boolean isCompatibleWith(Selection other);

        boolean contains(Lexer.Token tok);

    }

    enum MatchStatus { OK, NO_MATCH, EOI }

    class MatchingException extends LocatedParserException {

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

    void setSelection(Selection sel);

    TextLocation getCurrentPosition();

    Lexer.Token getCurrentToken();

    MatchStatus peek(boolean required) throws MatchingException;

    Lexer.Token next() throws MatchingException;

}
