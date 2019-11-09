package net.instant.util.parser;

import net.instant.util.LineColumnReader;

public class Parser {

    public static class ParserGrammar extends Grammar {

        public static final String START_SYMBOL = "$start";

        private Grammar reference;

        public ParserGrammar() {
            super();
        }
        public ParserGrammar(GrammarView copyFrom) {
            super(copyFrom);
        }
        public ParserGrammar(Production... productions) {
            super(productions);
        }

        public Grammar getReference() {
            return reference;
        }
        public void setReference(Grammar ref) {
            reference = ref;
        }

        protected boolean checkProductions(String name) {
            return (super.checkProductions(name) ||
                (reference != null && reference.checkProductions(name)));
        }
        public void validate() throws InvalidGrammarException {
            validate(START_SYMBOL);
        }

    }

    public interface Status {

        Lexer.Token getCurrentToken();

        void nextToken() throws ParsingException;

        void setState(State next);

        void pushState(State st, String treeNodeName);

        void popState() throws ParsingException;

    }

    public interface State {

        void apply(Status status) throws ParsingException;

    }

    public static class ParsingException extends LocatedParserException {

        public ParsingException(LineColumnReader.Coordinates pos) {
            super(pos);
        }
        public ParsingException(LineColumnReader.Coordinates pos,
                              String message) {
            super(pos, message);
        }
        public ParsingException(LineColumnReader.Coordinates pos,
                              Throwable cause) {
            super(pos, cause);
        }
        public ParsingException(LineColumnReader.Coordinates pos,
                              String message, Throwable cause) {
            super(pos, message, cause);
        }

    }

}
