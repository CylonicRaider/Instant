package net.instant.util.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import net.instant.util.LineColumnReader;
import net.instant.util.NamedSet;
import net.instant.util.NamedValue;

public class Parser {

    public static class ParserGrammar extends Grammar {

        public static final Symbol START_SYMBOL =
            Symbol.nonterminal("$start", 0);

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
            validate(START_SYMBOL.getContent());
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

        public boolean equals(Object other) {
            // As its name suggests, NullState has no distinctive features.
            return (other instanceof NullState);
        }

        public int hashCode() {
            return 0x57A7E; // "STATE".
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

        public String toString() {
            return String.format("%s@%h[symbol=%s,callState=%s]",
                getClass().getName(), this, getSymbol(), getCallState());
        }

        public boolean equals(Object other) {
            if (! (other instanceof PushState) || ! super.equals(other))
                return false;
            return getSymbol().equals(((PushState) other).getSymbol());
        }

        public int hashCode() {
            return super.hashCode() ^ getSymbol().hashCode();
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

        public String toString() {
            return String.format("%s@%h[expected=%s]", getClass().getName(),
                                 this, getExpected());
        }

        public boolean equals(Object other) {
            if (! (other instanceof LiteralState) || ! super.equals(other))
                return false;
            return getExpected().equals(((LiteralState) other).getExpected());
        }

        public int hashCode() {
            return super.hashCode() ^ getExpected().hashCode();
        }

        public Grammar.Symbol getExpected() {
            return expected;
        }

        public void apply(Status status) throws ParsingException {
            Lexer.Token tok = status.getCurrentToken();
            if (tok == null) {
                throw status.parsingException("Unexpected end of input at " +
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
            if (selector == null ||
                    selector.getType() == Grammar.SymbolType.ANYTHING) {
                return successors.get(null);
            } else if (selector.getType() != Grammar.SymbolType.NONTERMINAL) {
                return null;
            } else {
                return successors.get(selector.getContent());
            }
        }
        public void setSuccessor(Grammar.Symbol selector, State succ) {
            if (selector == null ||
                    selector.getType() == Grammar.SymbolType.ANYTHING) {
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
            if (! first) sb.append("(nothing)");
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
                        "end of input at " + status.getCurrentPosition() :
                        "token " + tok) +
                    ", expected one of " + formatSuccessors());
            status.setState(succ);
        }

    }

    protected static class Compiler implements Callable<State> {

        protected class StateInfo {

            private final State state;
            private int depth;
            private String anchorName;
            private Grammar.Symbol predSelector;
            private State predecessor;

            public StateInfo(State state) {
                this.state = state;
                this.depth = -1;
                this.anchorName = null;
                this.predSelector = null;
                this.predecessor = null;
            }

            public State getState() {
                return state;
            }

            public int getDepth() {
                return depth;
            }

            public String getAnchorName() {
                return anchorName;
            }
            public void setAnchorName(String name) {
                anchorName = name;
                depth = 0;
            }

            public Grammar.Symbol getPredecessorSelector() {
                return predSelector;
            }
            public State getPredecessor() {
                return predecessor;
            }
            public void setPredecessor(Grammar.Symbol selector, State pred) {
                if (pred == getState()) return;
                StateInfo predInfo = getStateInfo(pred);
                if (depth != -1 && predInfo.getDepth() >= depth) return;
                depth = predInfo.getDepth() + 1;
                predSelector = selector;
                predecessor = pred;
            }

            public StateInfo getPredecessorInfo() {
                State pred = getPredecessor();
                return (pred == null) ? null : getStateInfo(pred);
            }

            public String getPath() {
                String name = getAnchorName();
                if (name != null) return name;
                StateInfo predInfo = getPredecessorInfo();
                if (predInfo == null) return null;
                String predPath = predInfo.getPath();
                if (predPath == null) return null;
                Grammar.Symbol sel = getPredecessorSelector();
                return predPath + " -> " + ((sel == null) ? "(default)" :
                  sel.toUserString());
            }

            public String describe() {
                String ret = getState().toString();
                String path = getPath();
                if (path != null) ret += " (" + path + ")";
                return ret;
            }

        }

        private final ParserGrammar grammar;
        private final Set<String> seenProductions;
        private final Map<String, State> initialStates;
        private final Map<String, State> finalStates;
        private final Map<String, Set<Grammar.Symbol>> initialSymbolCache;
        private final Map<State, StateInfo> info;

        public Compiler(ParserGrammar grammar)
                throws InvalidGrammarException {
            this.grammar = new ParserGrammar(grammar);
            this.seenProductions = new HashSet<String>();
            this.initialStates = new HashMap<String, State>();
            this.finalStates = new HashMap<String, State>();
            this.initialSymbolCache = new HashMap<String,
                Set<Grammar.Symbol>>();
            this.info = new IdentityHashMap<State, StateInfo>();
            this.grammar.validate();
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
        protected StateInfo createStateInfo(State state) {
            return new StateInfo(state);
        }

        protected State getInitialState(String prodName) {
            State ret = initialStates.get(prodName);
            if (ret == null) {
                ret = createNullState();
                initialStates.put(prodName, ret);
                getStateInfo(ret).setAnchorName(prodName);
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
                boolean maybeEmpty = true;
                for (Grammar.Symbol s : sl) {
                    if ((s.getFlags() & Grammar.SYM_OPTIONAL) == 0) {
                        maybeEmpty = false;
                        break;
                    }
                }
                if (maybeEmpty) ret.add(null);
                if (sl.size() == 0) continue;
                Grammar.Symbol s = sl.get(0);
                if (s.getType() != Grammar.SymbolType.NONTERMINAL)
                    throw new InvalidGrammarException("First symbol of " +
                        "production " + prodName +
                        " alternative is a raw terminal");
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

        protected StateInfo getStateInfo(State state) {
            StateInfo ret = info.get(state);
            if (ret == null) {
                ret = createStateInfo(state);
                info.put(state, ret);
            }
            return ret;
        }
        protected String describeState(State state) {
            return getStateInfo(state).describe();
        }

        protected boolean selectorsEqual(Grammar.Symbol a, Grammar.Symbol b) {
            if (a != null && a.getType() == Grammar.SymbolType.ANYTHING)
                a = null;
            if (b != null && b.getType() == Grammar.SymbolType.ANYTHING)
                b = null;
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
                    "Cannot determine successor of state " +
                    describeState(prev));
            }
        }
        protected void addSuccessor(State prev, Grammar.Symbol selector,
                State next) throws InvalidGrammarException {
            if (prev instanceof MultiSuccessorState) {
                MultiSuccessorState cprev = (MultiSuccessorState) prev;
                if (cprev.getSuccessor(selector) != null)
                    throw new InvalidGrammarException(
                        "Ambiguous successors for state " +
                        describeState(prev) + " with selector " +
                        ((selector == null) ? "(default)" : selector) + ": " +
                        describeState(cprev.getSuccessor(selector)) +
                        " and " + describeState(next));
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
                    getStateInfo(newmid).setPredecessor(null, cprev);
                    prev = newmid;
                }
            } else {
                throw new IllegalArgumentException("Cannot splice into " +
                    "state graph after " + describeState(prev));
            }
            getStateInfo(next).setPredecessor(selector, prev);
        }

        @SuppressWarnings("fallthrough")
        protected void addProduction(Grammar.Production prod)
                throws InvalidGrammarException {
            List<State> prevs = new LinkedList<State>();
            Set<State> prevsIndex = new HashSet<State>();
            List<State> nextPrevs = new LinkedList<State>();
            Set<String> seenStates = new HashSet<String>();
            prevs.add(getInitialState(prod.getName()));
            prevsIndex.add(prevs.get(0));
            List<Grammar.Symbol> syms = prod.getSymbols();
            for (int i = 0; i < syms.size(); i++) {
                Grammar.Symbol sym = syms.get(i);
                State next;
                Set<Grammar.Symbol> selectors;
                switch (sym.getType()) {
                    case NONTERMINAL:
                        if (grammar.hasProductions(sym.getContent())) {
                            seenStates.clear();
                            selectors = findInitialSymbols(sym.getContent(),
                                                           seenStates);
                            /* Tail recursion optimization */
                            if (i == syms.size() - 1 &&
                                    sym.getContent().equals(prod.getName()) &&
                                    (sym.getFlags() & (Grammar.SYM_INLINE |
                                                       Grammar.SYM_DISCARD |
                                                       Grammar.SYM_REPEAT)
                                    ) == Grammar.SYM_INLINE) {
                                next = getInitialState(prod.getName());
                                break;
                            }
                            next = createPushState(sym);
                            addProductions(grammar.getRawProductions(
                                sym.getContent()));
                            break;
                        }
                    case TERMINAL: case PATTERN_TERMINAL: case ANYTHING:
                        next = createLiteralState(sym);
                        selectors = Collections.singleton(sym);
                        break;
                    default:
                        throw new AssertionError(
                            "Unrecognized symbol type?!");
                }
                boolean maybeEmpty = selectors.contains(null);
                if (maybeEmpty) {
                    selectors = new HashSet<Grammar.Symbol>(selectors);
                    selectors.remove(null);
                }
                if ((sym.getFlags() & Grammar.SYM_REPEAT) != 0) {
                    prevs.add(0, next);
                    prevsIndex.add(next);
                }
                boolean nextUsed = false;
                for (State pr : prevs) {
                    for (Grammar.Symbol sel : selectors) {
                        State st = getSuccessor(pr, sel);
                        // The mutual comparisons ensure that both next and st
                        // can veto the "merge".
                        if (st != null && next.equals(st) &&
                                st.equals(next)) {
                            nextPrevs.add(st);
                            continue;
                        }
                        addSuccessor(pr, sel, next);
                        nextUsed = true;
                    }
                }
                if (nextUsed) {
                    nextPrevs.add(next);
                }
                if (! maybeEmpty &&
                        (sym.getFlags() & Grammar.SYM_OPTIONAL) == 0) {
                    prevs.clear();
                    prevsIndex.clear();
                }
                for (State p : nextPrevs) {
                    if (prevsIndex.add(p))
                        prevs.add(0, p);
                }
                nextPrevs.clear();
            }
            State next = getFinalState(prod.getName());
            for (State pr : prevs) {
                addSuccessor(pr, null, next);
            }
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
                ParserGrammar.START_SYMBOL.getContent()));
            return getInitialState(ParserGrammar.START_SYMBOL.getContent());
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
