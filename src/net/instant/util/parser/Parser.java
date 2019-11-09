package net.instant.util.parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

        LineColumnReader.Coordinates getCurrentPosition();

        Lexer.Token getCurrentToken();

        void nextToken() throws ParsingException;

        void setState(State next);

        void pushState(State st, String treeNodeName);

        void popState() throws ParsingException;

        ParsingException parsingException(String message);

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

    public static class PushState implements State {

        private final String treeNodeName;
        private final State successor;
        private final State returnSuccessor;

        public PushState(String treeNodeName, State successor,
                         State returnSuccessor) {
            this.treeNodeName = treeNodeName;
            this.successor = successor;
            this.returnSuccessor = returnSuccessor;
        }

        public String getTreeNodeName() {
            return treeNodeName;
        }

        public State getSuccessor() {
            return successor;
        }

        public State getReturnSuccessor() {
            return returnSuccessor;
        }

        public void apply(Status status) {
            status.pushState(returnSuccessor, treeNodeName);
            status.setState(successor);
        }

    }

    public static class PopState implements State {

        public void apply(Status status) throws ParsingException {
            status.popState();
        }

    }

    public static class CheckState implements State {

        private final Grammar.Symbol expected;
        private final State successor;

        public CheckState(Grammar.Symbol expected, State successor) {
            this.expected = expected;
            this.successor = successor;
        }

        public Grammar.Symbol getExpected() {
            return expected;
        }

        public State getSuccessor() {
            return successor;
        }

        public void apply(Status status) throws ParsingException {
            Lexer.Token tok = status.getCurrentToken();
            if (tok == null) {
                throw status.parsingException("Unexpected EOF");
            } else if (! tok.matches(expected)) {
                throw status.parsingException("Unexpected token " + tok +
                    ", expected " + expected);
            } else {
                status.nextToken();
                status.setState(successor);
            }
        }

    }

    public static class BranchState implements State {

        private final Map<String, State> successors;

        public BranchState(Map<String, State> successors) {
            this.successors = Collections.unmodifiableMap(
                new HashMap<String, State>(successors));
        }

        public Map<String, State> getSuccessors() {
            return successors;
        }

        public String formatSuccessors() {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String pn : successors.keySet()) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(pn);
            }
            if (! first) sb.append("(N/A)");
            return sb.toString();
        }

        public void apply(Status status) throws ParsingException {
            Lexer.Token tok = status.getCurrentToken();
            if (tok == null)
                throw status.parsingException("Unexpected EOF");
            String prodName = tok.getProduction();
            if (prodName == null)
                throw status.parsingException("Invalid anonymous token");
            State succ = successors.get(prodName);
            if (succ == null)
                throw status.parsingException("Unexpected token " + tok +
                    ", expected one of " + formatSuccessors());
            status.setState(succ);
        }

    }

}
