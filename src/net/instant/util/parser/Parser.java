package net.instant.util.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.instant.util.LineColumnReader;
import net.instant.util.NamedValue;

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

    public interface ParseTree extends NamedValue {

        Lexer.Token getToken();

        List<ParseTree> getChildren();

        int childCount();

        ParseTree childAt(int index);

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

    public interface Status {

        LineColumnReader.Coordinates getCurrentPosition();

        Lexer.Token getCurrentToken() throws ParsingException;

        void nextToken() throws ParsingException;

        void storeToken(Lexer.Token tok);

        void setState(State next);

        void pushState(State st, String treeNodeName);

        void popState() throws ParsingException;

        ParsingException parsingException(String message);

    }

    public interface State {

        void apply(Status status) throws ParsingException;

    }

    public static class ParseTreeImpl implements ParseTree {

        private final String name;
        private final Lexer.Token token;
        private final List<ParseTree> children;
        private final List<ParseTree> childrenView;

        {
            children = new ArrayList<ParseTree>();
            childrenView = Collections.unmodifiableList(children);
        }

        public ParseTreeImpl(Lexer.Token token) {
            this.name = token.getProduction();
            this.token = token;
        }
        public ParseTreeImpl(String name) {
            this.name = name;
            this.token = null;
        }

        public String getName() {
            return name;
        }

        public Lexer.Token getToken() {
            return token;
        }

        public List<ParseTree> getChildren() {
            return childrenView;
        }

        protected List<ParseTree> getRawChildren() {
            return children;
        }

        public int childCount() {
            return children.size();
        }

        public ParseTree childAt(int index) {
            return children.get(index);
        }

        public void addChild(ParseTree ch) {
            getRawChildren().add(ch);
        }

    }

    private class StatusImpl implements Status {

        public LineColumnReader.Coordinates getCurrentPosition() {
            return getSource().getPosition();
        }

        public Lexer.Token getCurrentToken() throws ParsingException {
            try {
                return getSource().peek();
            } catch (IOException exc) {
                throw new ParsingException(getCurrentPosition(), exc);
            } catch (Lexer.LexingException exc) {
                throw new ParsingException(exc.getPosition(), exc);
            }
        }

        public void nextToken() throws ParsingException {
            try {
                getSource().read();
            } catch (IOException exc) {
                throw new ParsingException(getCurrentPosition(), exc);
            } catch (Lexer.LexingException exc) {
                throw new ParsingException(exc.getPosition(), exc);
            }
        }

        public void storeToken(Lexer.Token tok) {
            List<ParseTreeImpl> stack = getTreeStack();
            if (stack.size() == 0)
                throw new IllegalStateException(
                    "Trying to append token without a parse tree?!");
            ParseTreeImpl top = stack.get(stack.size() - 1);
            top.addChild(new ParseTreeImpl(tok));
        }

        public void setState(State next) {
            Parser.this.setState(next);
        }

        public void pushState(State st, String treeNodeName) {
            getTreeStack().add(new ParseTreeImpl(treeNodeName));
            getStateStack().add(st);
        }

        public void popState() throws ParsingException {
            List<State> stateStack = getStateStack();
            List<ParseTreeImpl> treeStack = getTreeStack();
            if (stateStack.isEmpty())
                throw new IllegalStateException(
                    "Attempting to pop empty stack!");
            stateStack.remove(stateStack.size() - 1);
            treeStack.remove(treeStack.size() - 1);
        }

        public ParsingException parsingException(String message) {
            return new ParsingException(getCurrentPosition(), message);
        }

    }

    public static class NullState implements State {

        private State successor;

        public NullState(State successor) {
            this.successor = successor;
        }
        public NullState() {
            this(null);
        }

        public State getSuccessor() {
            return successor;
        }
        public void setSuccessor(State s) {
            successor = s;
        }

        public void apply(Status status) {
            status.setState(successor);
        }

    }

    public static class PushState implements State {

        private final String treeNodeName;
        private State successor;
        private State returnSuccessor;

        public PushState(String treeNodeName, State successor,
                         State returnSuccessor) {
            this.treeNodeName = treeNodeName;
            this.successor = successor;
            this.returnSuccessor = returnSuccessor;
        }
        public PushState(String treeNodeName) {
            this(treeNodeName, null, null);
        }

        public String getTreeNodeName() {
            return treeNodeName;
        }

        public State getSuccessor() {
            return successor;
        }
        public void setSuccessor(State s) {
            successor = s;
        }

        public State getReturnSuccessor() {
            return returnSuccessor;
        }
        public void setReturnSuccessor(State s) {
            returnSuccessor = s;
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

    public static class LiteralState implements State {

        private final Grammar.Symbol expected;
        private State successor;

        public LiteralState(Grammar.Symbol expected, State successor) {
            this.expected = expected;
            this.successor = successor;
        }
        public LiteralState(Grammar.Symbol expected) {
            this(expected, null);
        }

        public Grammar.Symbol getExpected() {
            return expected;
        }

        public State getSuccessor() {
            return successor;
        }
        public void setSuccessor(State s) {
            successor = s;
        }

        public void apply(Status status) throws ParsingException {
            Lexer.Token tok = status.getCurrentToken();
            if (tok == null) {
                throw status.parsingException("Unexpected EOF");
            } else if (! tok.matches(expected)) {
                throw status.parsingException("Unexpected token " + tok +
                    ", expected " + expected);
            } else {
                status.storeToken(tok);
                status.nextToken();
                status.setState(successor);
            }
        }

    }

    public static class BranchState implements State {

        private final Map<String, State> successors;

        public BranchState(Map<String, State> successors) {
            this.successors = successors;
        }
        public BranchState() {
            this(new HashMap<String, State>());
        }

        public Map<String, State> getSuccessors() {
            return successors;
        }

        public void addSuccessor(String prodName, State st) {
            successors.put(prodName, st);
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

    protected static class Compiler {

        private final ParserGrammar grammar;
        private final Map<String, State> initialStates;
        private final Map<String, Set<String>> initialSymbolCache;

        public Compiler(Grammar grammar) {
            this.grammar = new ParserGrammar(grammar);
            this.initialStates = new HashMap<String, State>();
            this.initialSymbolCache = new HashMap<String, Set<String>>();
        }

        protected State getInitialState(String prodName) {
            State ret = initialStates.get(prodName);
            if (ret == null) {
                ret = new NullState(new PopState());
                initialStates.put(prodName, ret);
            }
            return ret;
        }

        protected Set<String> findInitialSymbols(String prodName,
                Set<String> seen) throws InvalidGrammarException {
            Set<String> ret = initialSymbolCache.get(prodName);
            if (ret != null)
                return ret;
            if (seen.contains(prodName))
                throw new InvalidGrammarException("Grammar is left-recursive");
            seen.add(prodName);
            ret = new HashSet<String>();
            for (Grammar.Production p : grammar.getRawProductions(prodName)) {
                List<Grammar.Symbol> sl = p.getSymbols();
                if (sl.size() == 0) continue;
                Grammar.Symbol s = sl.get(0);
                if (s.getType() != Grammar.SymbolType.NONTERMINAL)
                    throw new InvalidGrammarException("First symbol of a " +
                        "production alternative may not be a terminal");
                String c = s.getContent();
                if (grammar.getRawProductions().containsKey(c)) {
                    ret.addAll(findInitialSymbols(c, seen));
                } else {
                    ret.add(c);
                }
            }
            seen.remove(prodName);
            initialSymbolCache.put(prodName, ret);
            return ret;
        }

    }

    private final Lexer source;
    private final List<ParseTreeImpl> treeStack;
    private final List<State> stateStack;
    private final Status status;
    private State state;
    private ParseTree result;

    public Parser(Lexer source, State initialState) {
        this.source = source;
        this.treeStack = new ArrayList<ParseTreeImpl>();
        this.stateStack = new ArrayList<State>();
        this.status = new StatusImpl();
        this.state = initialState;
        this.result = null;
    }

    protected Lexer getSource() {
        return source;
    }

    private List<ParseTreeImpl> getTreeStack() {
        return treeStack;
    }

    private List<State> getStateStack() {
        return stateStack;
    }

    public Status getStatus() {
        return status;
    }

    protected State getState() {
        return state;
    }
    protected void setState(State st) {
        state = st;
    }

    public ParseTree getResult() {
        return result;
    }

    public ParseTree parse() throws ParsingException {
        if (result != null) return result;
        getTreeStack().add(new ParseTreeImpl((String) null));
        for (;;) {
            State st = getState();
            if (st == null) break;
            st.apply(getStatus());
        }
        if (getStateStack().size() != 0)
            throw getStatus().parsingException("Unfinished input");
        if (getTreeStack().size() != 1 ||
                getTreeStack().get(0).childCount() != 1)
            throw new IllegalStateException(
                "Internal parser state corrupted!");
        result = getTreeStack().remove(0).childAt(0);
        return result;
    }

}
