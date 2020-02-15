package net.instant.util.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import net.instant.util.IdentityLinkedSet;
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
        public ParserGrammar(GrammarView lexerGrammar, GrammarView copyFrom) {
            super(copyFrom);
            reference = new Lexer.LexerGrammar(lexerGrammar);
        }
        public ParserGrammar(GrammarView lexerGrammar,
                             Production... productions) {
            super(productions);
            reference = new Lexer.LexerGrammar(lexerGrammar);
        }

        protected String toStringDetail() {
            return "reference=" + reference;
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

        String getContent();

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

        void addExpectation(ExpectationSet exp);

        String formatExpectations();

        void setState(State next);

        void pushState(State st, String treeNodeName, int flags);

        void popState() throws ParsingException;

        ParsingException parsingException(String message);

        ParsingException unexpectedToken();

    }

    protected interface State {

        String toUserString();

        boolean matches(State other);

        void apply(Status status) throws ParsingException;

    }

    protected interface SingleSuccessorState extends State {

        Grammar.Symbol getSelector();

        State getSuccessor();

        void setSuccessor(Grammar.Symbol selector, State succ)
            throws BadSuccessorException;

    }

    protected interface MultiSuccessorState extends State {

        State getSuccessor(Grammar.Symbol selector);

        void setSuccessor(Grammar.Symbol selector, State succ)
            throws BadSuccessorException;

    }

    protected interface ExpectationSet {

        Set<String> getExpectedTokens();

    }

    protected static class BadSuccessorException extends Exception {

        public BadSuccessorException() {
            super();
        }
        public BadSuccessorException(String message) {
            super(message);
        }
        public BadSuccessorException(Throwable cause) {
            super(cause);
        }
        public BadSuccessorException(String message, Throwable cause) {
            super(message, cause);
        }

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

        public String getContent() {
            return (token == null) ? null : token.getContent();
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
            return getSource().getInputPosition();
        }

        public Lexer.Token getCurrentToken() throws ParsingException {
            try {
                return getSource().peek();
            } catch (Lexer.LexingException exc) {
                throw new ParsingException(exc.getPosition(), exc);
            }
        }

        public void nextToken() throws ParsingException {
            try {
                getSource().read();
            } catch (Lexer.LexingException exc) {
                throw new ParsingException(exc.getPosition(), exc);
            }
            getExpectations().clear();
        }

        public void storeToken(Lexer.Token tok) {
            List<ParseTreeImpl> stack = getTreeStack();
            if (stack.size() == 0)
                throw new IllegalStateException(
                    "Trying to append token without a parse tree?!");
            ParseTreeImpl top = stack.get(stack.size() - 1);
            top.addChild(new ParseTreeImpl(tok));
        }

        public void addExpectation(ExpectationSet exp) {
            getExpectations().add(exp);
        }

        public String formatExpectations() {
            Set<String> accum = new LinkedHashSet<String>();
            for (ExpectationSet exp : getExpectations()) {
                accum.addAll(exp.getExpectedTokens());
            }
            if (accum.isEmpty()) return "nothing";
            if (accum.contains(null)) return "anything";
            if (accum.size() == 1) return accum.iterator().next();
            StringBuilder sb = new StringBuilder("any of ");
            boolean first = true;
            for (String exp : accum) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(exp);
            }
            return sb.toString();
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

        public ParsingException unexpectedToken() {
            Lexer.Token tok;
            try {
                tok = getCurrentToken();
            } catch (ParsingException exc) {
                // Err... This *looks* like the caller's fault.
                throw new IllegalStateException("Complaining about an " +
                    "unexpected token without looking at it?!", exc);
            }
            return parsingException("Unexpected " +
                ((tok != null) ?
                    "token " + tok.toUserString() :
                    "end of input at " + getCurrentPosition()) +
                ", expected " + formatExpectations());
        }

    }

    protected static class NullState implements SingleSuccessorState {

        private Grammar.Symbol selector;
        private State successor;

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

        public String toUserString() {
            return String.format("%s@%h", getClass().getSimpleName(), this);
        }

        public boolean matches(State other) {
            // As its name suggests, NullState has no distinctive features
            // (this may, however, be different for subclasses).
            return (other instanceof NullState);
        }

        public void apply(Status status) throws ParsingException {
            status.setState(successor);
        }

    }

    protected static class CallState extends NullState {

        private final Grammar.Symbol sym;
        private State callState;

        public CallState(Grammar.Symbol sym, State callState) {
            this.sym = sym;
            this.callState = callState;
        }
        public CallState(Grammar.Symbol sym) {
            this(sym, null);
        }

        public String toString() {
            return String.format("%s@%h[symbol=%s,callState=%s]",
                getClass().getName(), this, getSymbol(), getCallState());
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

        public String toUserString() {
            return String.format("%s@%h[%s -> %s]",
                getClass().getSimpleName(), this, getSymbol().toUserString(),
                getCallState().toUserString());
        }

        public boolean matches(State other) {
            if (! (other instanceof CallState) || ! super.matches(other))
                return false;
            return getSymbol().equals(((CallState) other).getSymbol());
        }

        public void apply(Status status) {
            int flags = sym.getFlags();
            if (status.isKeepingAll()) flags &= ~Grammar.SYM_DISCARD;
            status.pushState(getSuccessor(), sym.getContent(), flags);
            status.setState(callState);
        }

    }

    protected static class ReturnState implements State {

        public String toUserString() {
            return String.format("%s@%h", getClass().getSimpleName(), this);
        }

        public boolean matches(State other) {
            return (other instanceof ReturnState);
        }

        public void apply(Status status) throws ParsingException {
            status.popState();
        }

    }

    protected static class LiteralState extends NullState
            implements ExpectationSet {

        private final Grammar.Symbol expected;

        public LiteralState(Grammar.Symbol expected) {
            this.expected = expected;
        }

        public String toString() {
            return String.format("%s@%h[expected=%s]", getClass().getName(),
                                 this, getExpected());
        }

        public Grammar.Symbol getExpected() {
            return expected;
        }

        public Set<String> getExpectedTokens() {
            return Collections.singleton(expected.toUserString());
        }

        public String toUserString() {
            return String.format("%s@%h[%s]", getClass().getSimpleName(),
                                 this, getExpected().toUserString());
        }

        public boolean matches(State other) {
            if (! (other instanceof LiteralState) || ! super.matches(other))
                return false;
            return getExpected().equals(((LiteralState) other).getExpected());
        }

        public void apply(Status status) throws ParsingException {
            status.addExpectation(this);
            Lexer.Token tok = status.getCurrentToken();
            if (tok == null || ! tok.matches(expected))
                throw status.unexpectedToken();
            if ((expected.getFlags() & Grammar.SYM_DISCARD) == 0 ||
                    status.isKeepingAll())
                status.storeToken(tok);
            status.nextToken();
            super.apply(status);
        }

    }

    protected static class BranchState implements MultiSuccessorState,
                                                  ExpectationSet {

        private final Map<String, State> successors;

        public BranchState(Map<String, State> successors) {
            this.successors = successors;
        }
        public BranchState() {
            this(new LinkedHashMap<String, State>());
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
        public void setSuccessor(Grammar.Symbol selector, State succ)
                throws BadSuccessorException {
            if (selector == null ||
                    selector.getType() == Grammar.SymbolType.ANYTHING) {
                successors.put(null, succ);
            } else if (selector.getType() != Grammar.SymbolType.NONTERMINAL) {
                throw new BadSuccessorException(
                    "Cannot branch on terminal Grammar symbols");
            } else {
                successors.put(selector.getContent(), succ);
            }
        }

        public Set<String> getExpectedTokens() {
            Set<String> ret = new LinkedHashSet<String>(successors.keySet());
            ret.remove(null);
            return ret;
        }

        public String toUserString() {
            return String.format("%s@%h", getClass().getSimpleName(), this);
        }

        public boolean matches(State other) {
            // Successors notwithstanding, every BranchState behaves like
            // every other.
            return (other instanceof BranchState);
        }

        public void apply(Status status) throws ParsingException {
            status.addExpectation(this);
            Lexer.Token tok = status.getCurrentToken();
            State succ = (tok == null) ? null :
                successors.get(tok.getProduction());
            if (succ == null)
                succ = successors.get(null);
            if (succ == null)
                throw status.unexpectedToken();
            status.setState(succ);
        }

    }

    protected static class EndState implements State, ExpectationSet {

        public Set<String> getExpectedTokens() {
            return Collections.singleton("end of input");
        }

        public String toUserString() {
            return String.format("%s@%h", getClass().getSimpleName(), this);
        }

        public boolean matches(State other) {
            return (other instanceof EndState);
        }

        public void apply(Status status) throws ParsingException {
            status.addExpectation(this);
            Lexer.Token tok = status.getCurrentToken();
            if (tok != null) throw status.unexpectedToken();
            status.setState(null);
        }

    }

    protected static class Compiler implements Callable<State> {

        protected class StateInfo {

            private final State state;
            private String anchorName;
            private Grammar.Symbol predSelector;
            private State predecessor;

            public StateInfo(State state) {
                this.state = state;
                this.anchorName = null;
                this.predSelector = null;
                this.predecessor = null;
            }

            public State getState() {
                return state;
            }

            public String getAnchorName() {
                return anchorName;
            }
            public void setAnchorName(String name) {
                anchorName = name;
            }

            public Grammar.Symbol getPredecessorSelector() {
                return predSelector;
            }
            public State getPredecessor() {
                return predecessor;
            }
            public void setPredecessor(Grammar.Symbol selector, State pred) {
                if (predecessor != null || anchorName != null) return;
                predSelector = selector;
                predecessor = pred;
            }
            public void clearPredecessor() {
                predSelector = null;
                predecessor = null;
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
                String ret = getState().toUserString();
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
        private State startState;

        public Compiler(ParserGrammar grammar)
                throws InvalidGrammarException {
            this.grammar = new ParserGrammar(grammar);
            this.seenProductions = new HashSet<String>();
            this.initialStates = new HashMap<String, State>();
            this.finalStates = new HashMap<String, State>();
            this.initialSymbolCache = new HashMap<String,
                Set<Grammar.Symbol>>();
            this.info = new IdentityHashMap<State, StateInfo>();
            this.startState = null;
            this.grammar.validate();
        }

        public ParserGrammar getGrammar() {
            return grammar;
        }

        protected NullState createNullState() {
            return new NullState();
        }
        protected CallState createCallState(Grammar.Symbol sym) {
            return new CallState(sym, getInitialState(sym.getContent()));
        }
        protected LiteralState createLiteralState(Grammar.Symbol sym) {
            return new LiteralState(sym);
        }
        protected BranchState createBranchState() {
            return new BranchState();
        }
        protected ReturnState createReturnState() {
            return new ReturnState();
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
                ret = createReturnState();
                finalStates.put(prodName, ret);
            }
            return ret;
        }

        private String formatCyclicalProductions(Collection<String> prodNames,
                                                 String lastProd) {
            StringBuilder sb = new StringBuilder();
            for (String pr : prodNames) {
                sb.append(pr).append(" -> ");
            }
            sb.append(lastProd);
            return sb.toString();
        }
        protected Set<Grammar.Symbol> findInitialSymbols(String prodName,
                Set<String> seen) throws InvalidGrammarException {
            Set<Grammar.Symbol> ret = initialSymbolCache.get(prodName);
            if (ret != null)
                return ret;
            if (seen.contains(prodName))
                throw new InvalidGrammarException(
                    "Grammar is left-recursive (cyclical productions " +
                    formatCyclicalProductions(seen, prodName) + ")");
            seen.add(prodName);
            ret = new LinkedHashSet<Grammar.Symbol>();
            boolean maybeEmpty = false;
            for (Grammar.Production p : grammar.getRawProductions(prodName)) {
                boolean productionMaybeEmpty = true;
                for (Grammar.Symbol s : p.getSymbols()) {
                    if (s.getType() != Grammar.SymbolType.NONTERMINAL)
                        throw new InvalidGrammarException(
                            "Start-significant symbol " + s +
                            " of production " + prodName +
                            " alternative is a raw terminal");
                    boolean symbolMaybeEmpty = ((s.getFlags() &
                        Grammar.SYM_OPTIONAL) != 0);
                    String c = s.getContent();
                    if (grammar.hasProductions(c)) {
                        Set<Grammar.Symbol> localSymbols =
                            findInitialSymbols(c, seen);
                        ret.addAll(localSymbols);
                        symbolMaybeEmpty |= localSymbols.contains(null);
                    } else {
                        ret.add(s);
                    }
                    if (! symbolMaybeEmpty) {
                        productionMaybeEmpty = false;
                        break;
                    }
                }
                maybeEmpty |= productionMaybeEmpty;
            }
            if (maybeEmpty) {
                ret.add(null);
            } else {
                ret.remove(null);
            }
            seen.remove(prodName);
            initialSymbolCache.put(prodName, ret);
            return ret;
        }
        protected Set<Grammar.Symbol> findInitialSymbols(String prodName)
                throws InvalidGrammarException {
            return findInitialSymbols(prodName, new LinkedHashSet<String>());
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
            return (a == null) ? (b == null) : a.equals(b);
        }
        protected State getSuccessor(State prev, Grammar.Symbol selector) {
            if (prev instanceof MultiSuccessorState) {
                return ((MultiSuccessorState) prev).getSuccessor(selector);
            } else if (prev instanceof SingleSuccessorState) {
                SingleSuccessorState cprev = (SingleSuccessorState) prev;
                State succ = cprev.getSuccessor();
                if (cprev.getSelector() == null &&
                        succ instanceof MultiSuccessorState) {
                    return getSuccessor(succ, selector);
                } else if (selectorsEqual(selector, cprev.getSelector())) {
                    return succ;
                } else {
                    return null;
                }
            } else {
                // See corresponding note in addSuccessor() about the
                // exception type.
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
                        ((selector == null) ? "(default)" :
                            selector.toUserString()) + ": " +
                        describeState(cprev.getSuccessor(selector)) +
                        " and " + describeState(next));
                try {
                    cprev.setSuccessor(selector, next);
                } catch (BadSuccessorException exc) {
                    throw new InvalidGrammarException(exc);
                }
            } else if (prev instanceof SingleSuccessorState) {
                SingleSuccessorState cprev = (SingleSuccessorState) prev;
                try {
                    State mid = cprev.getSuccessor();
                    if (mid == null) {
                        cprev.setSuccessor(selector, next);
                    } else if (mid instanceof MultiSuccessorState) {
                        addSuccessor(mid, selector, next);
                    } else {
                        Grammar.Symbol midSelector = cprev.getSelector();
                        BranchState newmid = createBranchState();
                        cprev.setSuccessor(null, newmid);
                        getStateInfo(newmid).clearPredecessor();
                        getStateInfo(newmid).setPredecessor(null, cprev);
                        addSuccessor(newmid, midSelector, mid);
                        addSuccessor(newmid, selector, next);
                        prev = newmid;
                    }
                } catch (BadSuccessorException exc) {
                    throw new InvalidGrammarException(
                        "Cannot add successor to state " +
                        describeState(prev) + ": " + exc.getMessage(), exc);
                } catch (InvalidGrammarException exc) {
                    throw new InvalidGrammarException(
                        "Cannot add successor to state " +
                        describeState(prev) + ": " + exc.getMessage(), exc);
                }
            } else {
                // While the grammar may (il)legitimately contain things that
                // are not allowed as successors at places where that matters,
                // and we reject that with the proper exception type in the
                // MultiSuccessorState case, it is the compiler's fault if
                // it generates a state that may not have successors (i.e.,
                // presently, a ReturnState) and then tries to put something
                // after it.
                throw new IllegalArgumentException("Cannot splice into " +
                    "state graph after " + describeState(prev));
            }
            getStateInfo(next).setPredecessor(selector, prev);
        }

        private Grammar.Symbol symbolWithFlags(Grammar.Symbol base,
                                               int newFlags) {
            if (base == null || base.getFlags() == newFlags) return base;
            return new Grammar.Symbol(base.getType(), base.getContent(),
                                      newFlags);
        }

        @SuppressWarnings("fallthrough")
        protected State compileSymbol(Grammar.Symbol sym, String prodName,
                int index, int count, Set<Grammar.Symbol> selectors)
                throws InvalidGrammarException {
            Grammar.Symbol cleanedSym = symbolWithFlags(sym,
                sym.getFlags() & ~COMPILER_FLAGS);
            switch (sym.getType()) {
                case NONTERMINAL:
                    if (grammar.hasProductions(sym.getContent())) {
                        for (Grammar.Symbol s : findInitialSymbols(
                                sym.getContent())) {
                            selectors.add(symbolWithFlags(s,
                                cleanedSym.getFlags()));
                        }
                        /* Tail recursion optimization */
                        if (index == count - 1 &&
                                sym.getContent().equals(prodName) &&
                                (sym.getFlags() & (Grammar.SYM_INLINE |
                                                   Grammar.SYM_DISCARD |
                                                   Grammar.SYM_REPEAT)
                                ) == Grammar.SYM_INLINE) {
                            return getInitialState(prodName);
                        }
                        addProductions(sym.getContent());
                        return createCallState(cleanedSym);
                    }
                case TERMINAL: case PATTERN_TERMINAL: case ANYTHING:
                    selectors.add(cleanedSym);
                    return createLiteralState(cleanedSym);
                default:
                    throw new AssertionError(
                        "Unrecognized symbol type?!");
            }
        }

        protected void addProduction(Grammar.Production prod)
                throws InvalidGrammarException {
            Set<Grammar.Symbol> selectors =
                new LinkedHashSet<Grammar.Symbol>();
            IdentityLinkedSet<State> prevs = new IdentityLinkedSet<State>();
            IdentityLinkedSet<State> nextPrevs =
                new IdentityLinkedSet<State>();
            prevs.add(getInitialState(prod.getName()));
            List<Grammar.Symbol> syms = prod.getSymbols();
            int symCount = syms.size();
            for (int i = 0; i < symCount; i++) {
                Grammar.Symbol sym = syms.get(i);
                State next = compileSymbol(sym, prod.getName(), i, symCount,
                                           selectors);
                boolean maybeEmpty = (selectors.contains(null) ||
                    (sym.getFlags() & Grammar.SYM_OPTIONAL) != 0);
                selectors.remove(null);
                for (State pr : prevs) {
                    for (Grammar.Symbol sel : selectors) {
                        State st = getSuccessor(pr, sel);
                        // The mutual comparisons ensure that both next and st
                        // can veto the "merge".
                        if (next.matches(st) && st.matches(next) &&
                                ! nextPrevs.contains(st))
                            nextPrevs.addLast(st);
                    }
                }
                if (nextPrevs.isEmpty()) {
                    nextPrevs.addFirst(next);
                } else {
                    next = nextPrevs.getFirst();
                }
                if ((sym.getFlags() & Grammar.SYM_REPEAT) != 0) {
                    prevs.addAllLast(nextPrevs);
                }
                for (State pr : prevs) {
                    for (Grammar.Symbol sel : selectors) {
                        if (! nextPrevs.contains(getSuccessor(pr, sel)))
                            addSuccessor(pr, sel, next);
                    }
                }
                if (! maybeEmpty) {
                    prevs.clear();
                }
                prevs.removeAll(nextPrevs);
                prevs.addAllFirst(nextPrevs);
                selectors.clear();
                nextPrevs.clear();
            }
            State next = getFinalState(prod.getName());
            for (State pr : prevs) {
                if (getSuccessor(pr, null) != next)
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
        protected void addProductions(String prodName)
                throws InvalidGrammarException {
            addProductions(grammar.getRawProductions(prodName));
        }

        protected State getStartState() throws InvalidGrammarException {
            if (startState == null) {
                addProductions(ParserGrammar.START_SYMBOL.getContent());
                startState = createCallState(ParserGrammar.START_SYMBOL);
                addSuccessor(startState, null, new EndState());
            }
            return startState;
        }

        public State call() throws InvalidGrammarException {
            return getStartState();
        }

    }

    /* Symbol flags that are handled by the parser compiler and thus need not
     * be considered for certain equivalence tests. */
    public static final int COMPILER_FLAGS = Grammar.SYM_OPTIONAL |
                                             Grammar.SYM_REPEAT;

    private final CompiledGrammar grammar;
    private final Lexer source;
    private final boolean keepAll;
    private final List<ExpectationSet> expectations;
    private final List<ParseTreeImpl> treeStack;
    private final List<State> stateStack;
    private final Status status;
    private State state;
    private ParseTree result;

    public Parser(CompiledGrammar grammar, Lexer source, boolean keepAll) {
        this.grammar = grammar;
        this.source = source;
        this.keepAll = keepAll;
        this.expectations = new ArrayList<ExpectationSet>();
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

    protected List<ExpectationSet> getExpectations() {
        return expectations;
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
        try {
            source.close();
        } catch (IOException exc) {
            throw new ParsingException(null,
                "Exception while closing lexer: " + exc, exc);
        }
        if (getStateStack().size() != 0 || getTreeStack().size() != 1 ||
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
