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

    protected interface Status {

        LineColumnReader.Coordinates getCurrentPosition();

        Lexer.Token getCurrentToken() throws ParsingException;

        void nextToken() throws ParsingException;

        void storeToken(Lexer.Token tok);

        void setState(State next);

        void pushState(State st, String treeNodeName);

        void popState() throws ParsingException;

        ParsingException parsingException(String message);

    }

    protected interface State {

        void apply(Status status) throws ParsingException;

    }

    protected interface SingleSuccessorState extends State {

        Grammar.Symbol getSelector();

        State getSuccessor();

        void setSuccessor(Grammar.Symbol selector, State succ);

    }

    protected interface MultiSuccessorState extends State {

        State getSuccessor(Grammar.Symbol selector);

        void setSuccessor(Grammar.Symbol selector, State succ);

    }

    protected static class ParseTreeImpl implements ParseTree {

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

    protected static class NullState implements SingleSuccessorState {

        private Grammar.Symbol selector;
        private State successor;

        public NullState(Grammar.Symbol selector, State successor) {
            this.selector = selector;
            this.successor = successor;
        }
        public NullState() {
            this(null, null);
        }

        public Grammar.Symbol getSelector() {
            return selector;
        }

        public State getSuccessor() {
            return successor;
        }

        public void setSuccessor(Grammar.Symbol sel, State st) {
            selector = sel;
            successor = st;
        }

        public void apply(Status status) throws ParsingException {
            status.setState(successor);
        }

    }

    protected static class PushState extends NullState {

        private final String treeNodeName;
        private State callState;

        public PushState(String treeNodeName, State callState,
                         Grammar.Symbol selector, State successor) {
            super(selector, successor);
            this.treeNodeName = treeNodeName;
            this.callState = callState;
        }
        public PushState(String treeNodeName) {
            this(treeNodeName, null, null, null);
        }

        public String getTreeNodeName() {
            return treeNodeName;
        }

        public State getCallState() {
            return callState;
        }
        public void setCallState(State s) {
            callState = s;
        }

        public void apply(Status status) {
            status.pushState(getSuccessor(), treeNodeName);
            status.setState(callState);
        }

    }

    protected static class PopState implements State {

        public void apply(Status status) throws ParsingException {
            status.popState();
        }

    }

    protected static class LiteralState extends NullState {

        private final Grammar.Symbol expected;

        public LiteralState(Grammar.Symbol expected, Grammar.Symbol selector,
                            State successor) {
            super(selector, successor);
            this.expected = expected;
        }
        public LiteralState(Grammar.Symbol expected) {
            this(expected, null, null);
        }

        public Grammar.Symbol getExpected() {
            return expected;
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
                super.apply(status);
            }
        }

    }

    protected static class BranchState implements MultiSuccessorState {

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

        public State getSuccessor(Grammar.Symbol selector) {
            if (selector.getType() != Grammar.SymbolType.NONTERMINAL)
                return null;
            return successors.get(selector.getContent());
        }
        public void setSuccessor(Grammar.Symbol selector, State succ) {
            if (selector.getType() != Grammar.SymbolType.NONTERMINAL)
                throw new IllegalArgumentException(
                    "Cannot branch on terminal Grammar symbols");
            successors.put(selector.getContent(), succ);
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
        private final Map<String, State> finalStates;
        private final Map<String, Set<Grammar.Symbol>> initialSymbolCache;

        public Compiler(Grammar grammar) {
            this.grammar = new ParserGrammar(grammar);
            this.initialStates = new HashMap<String, State>();
            this.finalStates = new HashMap<String, State>();
            this.initialSymbolCache = new HashMap<String,
                Set<Grammar.Symbol>>();
        }

        protected State getInitialState(String prodName) {
            State ret = initialStates.get(prodName);
            if (ret == null) {
                State end = new PopState();
                ret = new NullState(null, end);
                initialStates.put(prodName, ret);
                finalStates.put(prodName, end);
            }
            return ret;
        }
        protected State getFinalState(String prodName) {
            if (! finalStates.containsKey(prodName))
                getInitialState(prodName);
            return finalStates.get(prodName);
        }

        protected Set<Grammar.Symbol> findInitialSymbols(String prodName,
                Set<String> seen) throws InvalidGrammarException {
            Set<Grammar.Symbol> ret = initialSymbolCache.get(prodName);
            if (ret != null)
                return ret;
            if (seen.contains(prodName))
                throw new InvalidGrammarException("Grammar is left-recursive");
            seen.add(prodName);
            ret = new HashSet<Grammar.Symbol>();
            for (Grammar.Production p : grammar.getRawProductions(prodName)) {
                List<Grammar.Symbol> sl = p.getSymbols();
                if (sl.size() == 0) continue;
                Grammar.Symbol s = sl.get(0);
                if (s.getType() != Grammar.SymbolType.NONTERMINAL)
                    throw new InvalidGrammarException("First symbol of a " +
                        "production alternative may not be a terminal");
                String c = s.getContent();
                if (grammar.hasProductions(c)) {
                    ret.addAll(findInitialSymbols(c, seen));
                } else {
                    ret.add(s);
                }
            }
            seen.remove(prodName);
            initialSymbolCache.put(prodName, ret);
            return ret;
        }

        @SuppressWarnings("fallthrough")
        protected void addProduction(Grammar.Production prod)
                throws InvalidGrammarException {
            State cur = getInitialState(prod.getName());
            Set<String> seenStates = new HashSet<String>();
            for (Grammar.Symbol sym : prod.getSymbols()) {
                State next;
                Set<Grammar.Symbol> selectors;
                switch (sym.getType()) {
                    case NONTERMINAL:
                        if (grammar.hasProductions(sym.getContent())) {
                            next = new PushState(sym.getContent());
                            selectors = findInitialSymbols(sym.getContent(),
                                                           seenStates);
                            seenStates.clear();
                            break;
                        }
                    case TERMINAL: case PATTERN_TERMINAL:
                        next = new LiteralState(sym);
                        selectors = Collections.singleton(sym);
                        break;
                    default:
                        throw new AssertionError("This should not happen?!");
                }
                for (Grammar.Symbol sel : selectors) {
                    addSuccessor(cur, sel, next);
                }
                cur = next;
            }
        }

        protected static State getSuccessor(State prev,
                                            Grammar.Symbol selector) {
            if (prev instanceof MultiSuccessorState) {
                return ((MultiSuccessorState) prev).getSuccessor(selector);
            } else if (prev instanceof SingleSuccessorState) {
                SingleSuccessorState cprev = (SingleSuccessorState) prev;
                if (selector.equals(cprev.getSelector())) {
                    return cprev.getSuccessor();
                } else if (cprev.getSelector() != null) {
                    return null;
                }
                State mid = cprev.getSuccessor();
                if (mid instanceof MultiSuccessorState) {
                    return getSuccessor(mid, selector);
                } else {
                    return null;
                }
            } else {
                throw new IllegalArgumentException(
                    "Cannot determine successor of state " + prev);
            }
        }
        protected static void addSuccessor(State prev,
                Grammar.Symbol selector, State next) {
            if (prev instanceof MultiSuccessorState) {
                ((MultiSuccessorState) prev).setSuccessor(selector, next);
            } else if (prev instanceof SingleSuccessorState) {
                SingleSuccessorState cprev = (SingleSuccessorState) prev;
                State mid = cprev.getSuccessor();
                if (mid == null) {
                    cprev.setSuccessor(selector, next);
                    return;
                } else if (mid instanceof MultiSuccessorState) {
                    ((MultiSuccessorState) mid).setSuccessor(selector, next);
                } else {
                    BranchState newmid = new BranchState();
                    newmid.setSuccessor(cprev.getSelector(), mid);
                    newmid.setSuccessor(selector, next);
                    cprev.setSuccessor(null, newmid);
                }
            } else {
                throw new IllegalArgumentException("Cannot splice into " +
                    "state graph after " + prev);
            }
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
