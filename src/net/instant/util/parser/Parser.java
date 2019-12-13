package net.instant.util.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import net.instant.util.LineColumnReader;
import net.instant.util.NamedSet;
import net.instant.util.NamedValue;

public class Parser {

    public static class ParserGrammar extends Grammar {

        public static final String START_SYMBOL = "$start";

        private Lexer.LexerGrammar reference;

        public ParserGrammar() {
            super();
        }
        public ParserGrammar(ParserGrammar copyFrom) {
            super(copyFrom);
            reference = new Lexer.LexerGrammar(copyFrom.getReference());
        }
        public ParserGrammar(GrammarView copyFrom) {
            super(copyFrom);
        }
        public ParserGrammar(GrammarView lexerGrammar,
                             Production... productions) {
            super(productions);
            reference = new Lexer.LexerGrammar(lexerGrammar);
        }

        public Lexer.LexerGrammar getReference() {
            return reference;
        }
        public void setReference(Lexer.LexerGrammar ref) {
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

    public static class CompiledGrammar implements GrammarView {

        private final Lexer.CompiledGrammar lexerGrammar;
        private final ParserGrammar source;
        private final State initialState;

        protected CompiledGrammar(Lexer.CompiledGrammar lexerGrammar,
                                  ParserGrammar source, State initialState) {
            this.lexerGrammar = lexerGrammar;
            this.source = source;
            this.initialState = initialState;
        }

        protected Lexer.CompiledGrammar getLexerGrammar() {
            return lexerGrammar;
        }

        protected ParserGrammar getSource() {
            return source;
        }

        protected State getInitialState() {
            return initialState;
        }

        public Set<String> getProductionNames() {
            return source.getProductionNames();
        }

        public Set<Grammar.Production> getProductions(String name) {
            return source.getProductions(name);
        }

        public Parser makeParser(LineColumnReader input, boolean keepAll) {
            return new Parser(this, lexerGrammar.makeLexer(input), keepAll);
        }
        public Parser makeParser(Reader input, boolean keepAll) {
            return makeParser(new LineColumnReader(input), keepAll);
        }
        public Parser makeParser(LineColumnReader input) {
            return makeParser(input, false);
        }
        public Parser makeParser(Reader input) {
            return makeParser(new LineColumnReader(input), false);
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

        boolean isKeepingAll();

        LineColumnReader.Coordinates getCurrentPosition();

        Lexer.Token getCurrentToken() throws ParsingException;

        void nextToken() throws ParsingException;

        void storeToken(Lexer.Token tok);

        void setState(State next);

        void pushState(State st, String treeNodeName, int flags);

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

    protected class StatusImpl implements Status {

        public boolean isKeepingAll() {
            return Parser.this.isKeepingAll();
        }

        public LineColumnReader.Coordinates getCurrentPosition() {
            return new LineColumnReader.FixedCoordinates(
                getSource().getPosition());
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

        public void pushState(State st, String treeNodeName, int flags) {
            List<ParseTreeImpl> treeStack = getTreeStack();
            ParseTreeImpl top = treeStack.get(treeStack.size() - 1);
            if ((flags & Grammar.SYM_DISCARD) != 0) {
                treeStack.add(new ParseTreeImpl(treeNodeName));
            } else if ((flags & Grammar.SYM_INLINE) != 0) {
                treeStack.add(top);
            } else {
                ParseTreeImpl next = new ParseTreeImpl(treeNodeName);
                top.addChild(next);
                treeStack.add(next);
            }
            getStateStack().add(st);
        }

        public void popState() throws ParsingException {
            List<State> stateStack = getStateStack();
            List<ParseTreeImpl> treeStack = getTreeStack();
            if (stateStack.isEmpty())
                throw new IllegalStateException(
                    "Attempting to pop empty stack!");
            State next = stateStack.remove(stateStack.size() - 1);
            treeStack.remove(treeStack.size() - 1);
            setState(next);
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

        private final Grammar.Symbol sym;
        private State callState;

        public PushState(Grammar.Symbol sym, State callState,
                         Grammar.Symbol selector, State successor) {
            super(selector, successor);
            this.sym = sym;
            this.callState = callState;
        }
        public PushState(Grammar.Symbol sym) {
            this(sym, null, null, null);
        }

        public Grammar.Symbol getSymbol() {
            return sym;
        }

        public State getCallState() {
            return callState;
        }
        public void setCallState(State s) {
            callState = s;
        }

        public void apply(Status status) {
            int flags = sym.getFlags();
            if (status.isKeepingAll()) flags &= ~Grammar.SYM_DISCARD;
            status.pushState(getSuccessor(), sym.getContent(), flags);
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
                throw status.parsingException("Unexpected EOF at " +
                    status.getCurrentPosition());
            } else if (! tok.matches(expected)) {
                throw status.parsingException("Unexpected token " +
                    tok.toUserString() + ", expected " +
                    expected.toUserString());
            } else {
                if ((expected.getFlags() & Grammar.SYM_DISCARD) == 0 ||
                        status.isKeepingAll())
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
            if (selector == null) {
                return successors.get(null);
            } else if (selector.getType() != Grammar.SymbolType.NONTERMINAL) {
                return null;
            } else {
                return successors.get(selector.getContent());
            }
        }
        public void setSuccessor(Grammar.Symbol selector, State succ) {
            if (selector == null) {
                successors.put(null, succ);
            } else if (selector.getType() != Grammar.SymbolType.NONTERMINAL) {
                throw new IllegalArgumentException(
                    "Cannot branch on terminal Grammar symbols");
            } else {
                successors.put(selector.getContent(), succ);
            }
        }

        public String formatSuccessors() {
            if (successors.containsKey(null)) return "(anything)";
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
            if (! first) sb.append("(none)");
            return sb.toString();
        }

        public void apply(Status status) throws ParsingException {
            Lexer.Token tok = status.getCurrentToken();
            State succ = (tok == null) ? null :
                successors.get(tok.getProduction());
            if (succ == null)
                succ = successors.get(null);
            if (succ == null)
                throw status.parsingException("Unexpected " +
                    ((tok == null) ?
                        "EOF at " + status.getCurrentPosition() :
                        "token " + tok) +
                    ", expected one of " + formatSuccessors());
            status.setState(succ);
        }

    }

    protected static class Compiler implements Callable<State> {

        private final ParserGrammar grammar;
        private final Set<String> seenProductions;
        private final Map<String, State> initialStates;
        private final Map<String, State> finalStates;
        private final Map<String, Set<Grammar.Symbol>> initialSymbolCache;

        public Compiler(ParserGrammar grammar) {
            this.grammar = new ParserGrammar(grammar);
            this.seenProductions = new HashSet<String>();
            this.initialStates = new HashMap<String, State>();
            this.finalStates = new HashMap<String, State>();
            this.initialSymbolCache = new HashMap<String,
                Set<Grammar.Symbol>>();
        }

        public ParserGrammar getGrammar() {
            return grammar;
        }

        protected NullState createNullState() {
            return new NullState();
        }
        protected PushState createPushState(Grammar.Symbol sym) {
            return new PushState(sym);
        }
        protected LiteralState createLiteralState(Grammar.Symbol sym) {
            return new LiteralState(sym);
        }
        protected BranchState createBranchState() {
            return new BranchState();
        }
        protected PopState createPopState() {
            return new PopState();
        }

        protected State getInitialState(String prodName) {
            State ret = initialStates.get(prodName);
            if (ret == null) {
                ret = createNullState();
                initialStates.put(prodName, ret);
            }
            return ret;
        }
        protected State getFinalState(String prodName) {
            State ret = finalStates.get(prodName);
            if (ret == null) {
                ret = createPopState();
                finalStates.put(prodName, ret);
            }
            return ret;
        }

        protected Set<Grammar.Symbol> findInitialSymbols(String prodName,
                Set<String> seen) throws InvalidGrammarException {
            Set<Grammar.Symbol> ret = initialSymbolCache.get(prodName);
            if (ret != null)
                return ret;
            if (seen.contains(prodName))
                throw new InvalidGrammarException(
                    "Grammar is left-recursive");
            seen.add(prodName);
            ret = new HashSet<Grammar.Symbol>();
            for (Grammar.Production p : grammar.getRawProductions(prodName)) {
                List<Grammar.Symbol> sl = p.getSymbols();
                if (sl.size() == 0) continue;
                Grammar.Symbol s = sl.get(0);
                if (s.getType() != Grammar.SymbolType.NONTERMINAL)
                    throw new InvalidGrammarException("First symbol of " +
                        "production " + prodName +
                        " alternative is a terminal");
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

        protected boolean selectorsEqual(Grammar.Symbol a, Grammar.Symbol b) {
            return (a == null) ? (b == null) : a.equals(b);
        }
        protected State getSuccessor(State prev, Grammar.Symbol selector) {
            if (prev instanceof MultiSuccessorState) {
                return ((MultiSuccessorState) prev).getSuccessor(selector);
            } else if (prev instanceof SingleSuccessorState) {
                SingleSuccessorState cprev = (SingleSuccessorState) prev;
                if (selectorsEqual(selector, cprev.getSelector())) {
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
        protected void addSuccessor(State prev, Grammar.Symbol selector,
                State next) throws InvalidGrammarException {
            if (prev instanceof MultiSuccessorState) {
                MultiSuccessorState cprev = (MultiSuccessorState) prev;
                if (cprev.getSuccessor(selector) != null)
                    throw new InvalidGrammarException(
                        "Ambiguous successors for state " + prev +
                        " with selector " +
                        ((selector == null) ? "(default)" : selector) + ": " +
                        cprev.getSuccessor(selector) + " and " + next);
                cprev.setSuccessor(selector, next);
            } else if (prev instanceof SingleSuccessorState) {
                SingleSuccessorState cprev = (SingleSuccessorState) prev;
                State mid = cprev.getSuccessor();
                if (mid == null) {
                    cprev.setSuccessor(selector, next);
                } else if (mid instanceof MultiSuccessorState) {
                    addSuccessor(mid, selector, next);
                } else {
                    BranchState newmid = createBranchState();
                    addSuccessor(newmid, cprev.getSelector(), mid);
                    addSuccessor(newmid, selector, next);
                    cprev.setSuccessor(null, newmid);
                }
            } else {
                throw new IllegalArgumentException("Cannot splice into " +
                    "state graph after " + prev);
            }
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
                            next = createPushState(sym);
                            selectors = findInitialSymbols(sym.getContent(),
                                                           seenStates);
                            seenStates.clear();
                            addProductions(grammar.getRawProductions(
                                sym.getContent()));
                            break;
                        }
                    case TERMINAL: case PATTERN_TERMINAL:
                        next = createLiteralState(sym);
                        selectors = Collections.singleton(sym);
                        break;
                    default:
                        throw new AssertionError(
                            "Unrecognized symbol type?!");
                }
                for (Grammar.Symbol sel : selectors) {
                    addSuccessor(cur, sel, next);
                }
                cur = next;
            }
            addSuccessor(cur, null, getFinalState(prod.getName()));
        }

        protected void addProductions(NamedSet<Grammar.Production> prods)
                throws InvalidGrammarException {
            if (seenProductions.contains(prods.getName())) return;
            seenProductions.add(prods.getName());
            for (Grammar.Production p : prods) {
                addProduction(p);
            }
        }

        public State call() throws InvalidGrammarException {
            addProductions(grammar.getRawProductions(
                ParserGrammar.START_SYMBOL));
            return getInitialState(ParserGrammar.START_SYMBOL);
        }

    }

    private final CompiledGrammar grammar;
    private final Lexer source;
    private final boolean keepAll;
    private final List<ParseTreeImpl> treeStack;
    private final List<State> stateStack;
    private final Status status;
    private State state;
    private ParseTree result;

    public Parser(CompiledGrammar grammar, Lexer source, boolean keepAll) {
        this.grammar = grammar;
        this.source = source;
        this.keepAll = keepAll;
        this.treeStack = new ArrayList<ParseTreeImpl>();
        this.stateStack = new ArrayList<State>();
        this.status = new StatusImpl();
        this.state = grammar.getInitialState();
        this.result = null;
    }

    public CompiledGrammar getGrammar() {
        return grammar;
    }

    protected Lexer getSource() {
        return source;
    }

    public boolean isKeepingAll() {
        return keepAll;
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
            throw getStatus().parsingException("Unfinished input at " +
                getStatus().getCurrentPosition());
        if (getTreeStack().size() != 1 ||
                getTreeStack().get(0).childCount() != 1)
            throw new IllegalStateException(
                "Internal parser state corrupted!");
        result = getTreeStack().remove(0).childAt(0);
        return result;
    }

    public static CompiledGrammar compile(ParserGrammar g)
            throws InvalidGrammarException {
        Compiler comp = new Compiler(g);
        ParserGrammar gc = comp.getGrammar();
        return new CompiledGrammar(Lexer.compile(gc.getReference()),
                                   gc, comp.call());
    }

}
