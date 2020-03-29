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

public class Parser {

    public static class CompiledGrammar implements GrammarView {

        private final Grammar source;
        private final State initialState;

        protected CompiledGrammar(Grammar source, State initialState) {
            this.source = source;
            this.initialState = initialState;
        }

        protected Grammar getSource() {
            return source;
        }

        protected State getInitialState() {
            return initialState;
        }

        public Grammar.Nonterminal getStartSymbol() {
            return source.getStartSymbol();
        }

        public Set<String> getProductionNames() {
            return source.getProductionNames();
        }

        public Set<Grammar.Production> getProductions(String name) {
            return source.getProductions(name);
        }

        public Lexer makeLexer(LineColumnReader input) {
            return new Lexer(input);
        }
        public Lexer makeLexer(Reader input) {
            return makeLexer(new LineColumnReader(input));
        }

        public Parser makeParser(TokenSource source, boolean keepAll) {
            return new Parser(this, source, keepAll);
        }
        public Parser makeParser(TokenSource source) {
            return makeParser(source, false);
        }

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

        Lexer.MatchStatus getCurrentTokenStatus() throws ParsingException;

        Token getCurrentToken(boolean required) throws ParsingException;

        void nextToken() throws ParsingException;

        void storeToken(Token tok);

        void setExpectation(ExpectationSet exp);

        String formatExpectations();

        void setState(State next);

        void pushState(State st, String treeNodeName, int flags);

        void popState() throws ParsingException;

        ParsingException parsingException(String message);

        ParsingException unexpectedToken();

    }

    protected interface State {

        String toUserString();

        Collection<State> getAllSuccessors();

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

        Set<Grammar.Symbol> getExpectedTokens();

        TokenSource.Selection getSelection();

        void setLexerState(TokenSource.Selection st);

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
        private final Token token;
        private final List<ParseTree> children;
        private final List<ParseTree> childrenView;

        {
            children = new ArrayList<ParseTree>();
            childrenView = Collections.unmodifiableList(children);
        }

        public ParseTreeImpl(Token token) {
            this.name = token.getName();
            this.token = token;
        }
        public ParseTreeImpl(String name) {
            this.name = name;
            this.token = null;
        }

        public String getName() {
            return name;
        }

        public Token getToken() {
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

        private ParsingException wrap(TokenSource.MatchingException exc) {
            return new ParsingException(exc.getPosition(), exc.getMessage(),
                                        exc);
        }

        public boolean isKeepingAll() {
            return Parser.this.isKeepingAll();
        }

        public LineColumnReader.Coordinates getCurrentPosition() {
            return getTokenSource().getCurrentPosition();
        }

        public Lexer.MatchStatus getCurrentTokenStatus()
                throws ParsingException {
            try {
                return getTokenSource().peek(false);
            } catch (TokenSource.MatchingException exc) {
                throw wrap(exc);
            }
        }

        public Token getCurrentToken(boolean required)
                throws ParsingException {
            try {
                Lexer.MatchStatus st = getTokenSource().peek(required);
                if (st == Lexer.MatchStatus.OK) {
                    return getTokenSource().getCurrentToken();
                } else {
                    return null;
                }
            } catch (TokenSource.MatchingException exc) {
                throw wrap(exc);
            }
        }

        public void nextToken() throws ParsingException {
            try {
                getTokenSource().next();
            } catch (TokenSource.MatchingException exc) {
                throw wrap(exc);
            }
            getExpectations().clear();
        }

        public void storeToken(Token tok) {
            List<ParseTreeImpl> stack = getTreeStack();
            if (stack.size() == 0)
                throw new IllegalStateException(
                    "Trying to append token without a parse tree?!");
            ParseTreeImpl top = stack.get(stack.size() - 1);
            top.addChild(new ParseTreeImpl(tok));
        }

        public void setExpectation(ExpectationSet exp) {
            getExpectations().add(exp);
            getTokenSource().setSelection(exp.getSelection());
        }

        public String formatExpectations() {
            Set<String> accum = new LinkedHashSet<String>();
            for (ExpectationSet exp : getExpectations()) {
                for (Grammar.Symbol sym : exp.getExpectedTokens()) {
                    if (sym == null) {
                        accum.add("end of input");
                    } else {
                        accum.add(sym.toString());
                    }
                }
            }
            if (accum.isEmpty()) return "nothing";
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
            if ((flags & Grammar.Symbol.SYM_DISCARD) != 0) {
                treeStack.add(new ParseTreeImpl(treeNodeName));
            } else if ((flags & Grammar.Symbol.SYM_INLINE) != 0) {
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
            Token tok;
            try {
                tok = getCurrentToken(true);
            } catch (ParsingException exc) {
                // Good enough. The exception could, in particular, be an
                // "unexpected character" error, which we definitely want to
                // propagate.
                return exc;
            }
            return parsingException("Unexpected " +
                ((tok != null) ?
                    "token " + tok :
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

        public Collection<State> getAllSuccessors() {
            return (successor == null) ? Collections.<State>emptySet() :
                                         Collections.singleton(successor);
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

        private final Grammar.Nonterminal sym;
        private State callState;

        public CallState(Grammar.Nonterminal sym, State callState) {
            this.sym = sym;
            this.callState = callState;
        }
        public CallState(Grammar.Nonterminal sym) {
            this(sym, null);
        }

        public String toString() {
            return String.format("%s@%h[symbol=%s,callState=%s]",
                getClass().getName(), this, getSymbol(), getCallState());
        }

        public Grammar.Nonterminal getSymbol() {
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
                getClass().getSimpleName(), this, getSymbol().toString(),
                getCallState().toUserString());
        }

        public Collection<State> getAllSuccessors() {
            Set<State> ret = new HashSet<>(super.getAllSuccessors());
            if (callState != null) ret.add(callState);
            return ret;
        }

        public boolean matches(State other) {
            if (! (other instanceof CallState) || ! super.matches(other))
                return false;
            return getSymbol().equals(((CallState) other).getSymbol());
        }

        public void apply(Status status) {
            int flags = sym.getFlags();
            if (status.isKeepingAll()) flags &= ~Grammar.Symbol.SYM_DISCARD;
            status.pushState(getSuccessor(), sym.getReference(), flags);
            status.setState(callState);
        }

    }

    protected static class ReturnState implements State {

        public String toUserString() {
            return String.format("%s@%h", getClass().getSimpleName(), this);
        }

        public Collection<State> getAllSuccessors() {
            return Collections.emptySet();
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
        private TokenSource.Selection lexerState;

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

        public TokenSource.Selection getSelection() {
            return lexerState;
        }

        public void setLexerState(TokenSource.Selection state) {
            lexerState = state;
        }

        public Set<Grammar.Symbol> getExpectedTokens() {
            return Collections.singleton(expected);
        }

        public String toUserString() {
            return String.format("%s@%h[%s]", getClass().getSimpleName(),
                                 this, getExpected().toString());
        }

        public boolean matches(State other) {
            if (! (other instanceof LiteralState) || ! super.matches(other))
                return false;
            return getExpected().equals(((LiteralState) other).getExpected());
        }

        public void apply(Status status) throws ParsingException {
            status.setExpectation(this);
            Token tok = status.getCurrentToken(true);
            if (tok == null || ! tok.matches(expected))
                throw status.unexpectedToken();
            if ((expected.getFlags() & Grammar.Symbol.SYM_DISCARD) == 0 ||
                    status.isKeepingAll())
                status.storeToken(tok);
            status.nextToken();
            super.apply(status);
        }

    }

    protected static class BranchState implements MultiSuccessorState,
                                                  ExpectationSet {

        private final Map<String, State> successors;
        private TokenSource.Selection lexerState;

        public BranchState(Map<String, State> successors) {
            this.successors = successors;
        }
        public BranchState() {
            this(new LinkedHashMap<String, State>());
        }

        public Map<String, State> getSuccessors() {
            return successors;
        }

        public Collection<State> getAllSuccessors() {
            return successors.values();
        }

        public State getSuccessor(Grammar.Symbol selector) {
            if (selector == null) {
                return successors.get(null);
            } else if (! (selector instanceof Grammar.Nonterminal)) {
                return null;
            } else {
                return successors.get(
                    ((Grammar.Nonterminal) selector).getReference());
            }
        }
        public void setSuccessor(Grammar.Symbol selector, State succ)
                throws BadSuccessorException {
            if (selector == null) {
                successors.put(null, succ);
            } else if (! (selector instanceof Grammar.Nonterminal)) {
                throw new BadSuccessorException(
                    "Cannot branch on terminal Grammar symbols");
            } else {
                successors.put(
                    ((Grammar.Nonterminal) selector).getReference(), succ);
            }
        }

        public TokenSource.Selection getSelection() {
            return lexerState;
        }

        public void setLexerState(TokenSource.Selection state) {
            lexerState = state;
        }

        public Set<Grammar.Symbol> getExpectedTokens() {
            Set<Grammar.Symbol> ret = new LinkedHashSet<Grammar.Symbol>();
            for (String key : successors.keySet()) {
                if (key == null) continue;
                ret.add(new Grammar.Nonterminal(key, 0));
            }
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
            status.setExpectation(this);
            Token tok = status.getCurrentToken(false);
            State succ = (tok == null) ? null : successors.get(tok.getName());
            if (succ == null)
                succ = successors.get(null);
            if (succ == null)
                throw status.unexpectedToken();
            status.setState(succ);
        }

    }

    protected static class EndState implements State, ExpectationSet {

        private TokenSource.Selection lexerState;

        public TokenSource.Selection getSelection() {
            return lexerState;
        }

        public void setLexerState(TokenSource.Selection state) {
            lexerState = state;
        }

        public Set<Grammar.Symbol> getExpectedTokens() {
            return Collections.singleton(null);
        }

        public String toUserString() {
            return String.format("%s@%h", getClass().getSimpleName(), this);
        }

        public Collection<State> getAllSuccessors() {
            return Collections.emptySet();
        }

        public boolean matches(State other) {
            return (other instanceof EndState);
        }

        public void apply(Status status) throws ParsingException {
            status.setExpectation(this);
            if (status.getCurrentTokenStatus() != Lexer.MatchStatus.EOI)
                throw status.unexpectedToken();
            status.setState(null);
        }

    }

    protected static class Compiler implements Callable<State> {

        protected class StateInfo {

            private final State state;
            private final Set<State> intransitiveSuccessors;
            private String anchorName;
            private Grammar.Symbol predSelector;
            private State predecessor;

            public StateInfo(State state) {
                this.state = state;
                this.intransitiveSuccessors = new HashSet<State>();
                this.anchorName = null;
                this.predSelector = null;
                this.predecessor = null;
            }

            public State getState() {
                return state;
            }

            public Set<State> getIntransitiveSuccessors() {
                return intransitiveSuccessors;
            }
            public boolean hasIntransitiveSuccessor(State other) {
                return intransitiveSuccessors.contains(other);
            }
            public void addIntransitiveSuccessor(State other) {
                intransitiveSuccessors.add(other);
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
                  sel.toString());
            }

            public String describe() {
                String ret = getState().toUserString();
                String path = getPath();
                if (path != null) ret += " (" + path + ")";
                return ret;
            }

        }

        private final Grammar grammar;
        private final Set<String> seenProductions;
        private final Map<String, TokenPattern> tokens;
        private final Map<Set<Grammar.Symbol>,
                          TokenSource.Selection> lexerStates;
        private final Map<String, State> initialStates;
        private final Map<String, State> finalStates;
        private final Map<String, Set<Grammar.Symbol>> initialSymbolCache;
        private final Map<State, StateInfo> info;
        private State startState;

        public Compiler(GrammarView grammar)
                throws InvalidGrammarException {
            this.grammar = new Grammar(grammar);
            this.seenProductions = new HashSet<String>();
            this.tokens = new HashMap<String, TokenPattern>();
            this.lexerStates = new HashMap<Set<Grammar.Symbol>,
                                           TokenSource.Selection>();
            this.initialStates = new HashMap<String, State>();
            this.finalStates = new HashMap<String, State>();
            this.initialSymbolCache = new HashMap<String,
                                                  Set<Grammar.Symbol>>();
            this.info = new IdentityHashMap<State, StateInfo>();
            this.startState = null;
            this.grammar.validate();
        }

        public Grammar getGrammar() {
            return grammar;
        }

        protected TokenSource.Selection createLexerState(
                Map<String, TokenPattern> tokens) {
            return new Lexer.StandardState(tokens);
        }
        protected NullState createNullState() {
            return new NullState();
        }
        protected CallState createCallState(Grammar.Nonterminal sym) {
            return new CallState(sym, getInitialState(sym.getReference()));
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
        protected EndState createEndState() {
            return new EndState();
        }
        protected StateInfo createStateInfo(State state) {
            return new StateInfo(state);
        }

        protected TokenPattern compileToken(String name)
                throws InvalidGrammarException {
            Set<Grammar.Production> prods = grammar.getRawProductions(name);
            if (prods.size() != 1) return null;
            Grammar.Production prod = prods.iterator().next();
            if (prod.getSymbols().size() != 1) return null;
            Grammar.Symbol sym = prod.getSymbols().get(0);
            if (sym.getFlags() != Grammar.Symbol.SYM_INLINE) return null;
            TokenPattern ret = TokenPattern.create(name, prods);
            return ret;
        }
        protected TokenPattern getToken(String name)
                throws InvalidGrammarException {
            if (! tokens.containsKey(name))
                tokens.put(name, compileToken(name));
            return tokens.get(name);
        }

        protected TokenSource.Selection getSelection(Set<Grammar.Symbol> syms)
                throws InvalidGrammarException {
            TokenSource.Selection ret = lexerStates.get(syms);
            if (ret == null) {
                Map<String, TokenPattern> tokens =
                    new LinkedHashMap<String, TokenPattern>();
                for (Grammar.Symbol s : syms) {
                    if (s == null)
                        continue;
                    if (! (s instanceof Grammar.Nonterminal))
                        throw new InvalidGrammarException("Token " +
                            "definition set contains a raw terminal " + s);
                    String cnt = ((Grammar.Nonterminal) s).getReference();
                    TokenPattern tok = null;
                    Throwable error = null;
                    try {
                        tok = getToken(cnt);
                    } catch (InvalidGrammarException exc) {
                        error = exc;
                    }
                    if (tok == null)
                        throw new IllegalStateException(
                            "Unrecognized token " + cnt + "?!", error);
                    tokens.put(tok.getName(), tok);
                }
                ret = createLexerState(tokens);
                lexerStates.put(new HashSet<Grammar.Symbol>(syms), ret);
            }
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
                    if (! (s instanceof Grammar.Nonterminal))
                        throw new InvalidGrammarException(
                            "Start-significant symbol " + s +
                            " of production " + prodName +
                            " alternative is a raw terminal");
                    boolean symbolMaybeEmpty = ((s.getFlags() &
                        Grammar.Symbol.SYM_OPTIONAL) != 0);
                    String c = ((Grammar.Nonterminal) s).getReference();
                    if (getToken(c) == null) {
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

        protected boolean selectorsEqual(Grammar.Symbol a, Grammar.Symbol b) {
            return (a == null) ? (b == null) : a.equals(b);
        }
        protected Collection<State> getAllSuccessors(State prev) {
            return prev.getAllSuccessors();
        }
        protected State getSuccessor(State prev, Grammar.Symbol selector,
                                     boolean intransitive) {
            State ret;
            if (prev instanceof MultiSuccessorState) {
                ret = ((MultiSuccessorState) prev).getSuccessor(selector);
            } else if (prev instanceof SingleSuccessorState) {
                SingleSuccessorState cprev = (SingleSuccessorState) prev;
                State succ = cprev.getSuccessor();
                if (cprev.getSelector() == null &&
                        succ instanceof MultiSuccessorState) {
                    ret = getSuccessor(succ, selector, false);
                } else if (selectorsEqual(selector, cprev.getSelector())) {
                    ret = succ;
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
            if (intransitive &&
                    ! getStateInfo(prev).hasIntransitiveSuccessor(ret))
                return null;
            return ret;
        }
        protected void addSuccessor(State prev, Grammar.Symbol selector,
                State next, boolean intransitive)
                throws InvalidGrammarException {
            if (prev instanceof MultiSuccessorState) {
                MultiSuccessorState cprev = (MultiSuccessorState) prev;
                if (cprev.getSuccessor(selector) != null)
                    throw new InvalidGrammarException(
                        "Ambiguous successors for state " +
                        describeState(prev) + " with selector " +
                        ((selector == null) ? "(default)" :
                            selector.toString()) + ": " +
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
                        addSuccessor(mid, selector, next, false);
                    } else {
                        Grammar.Symbol midSelector = cprev.getSelector();
                        BranchState newmid = createBranchState();
                        cprev.setSuccessor(null, newmid);
                        addSuccessor(newmid, midSelector, mid, false);
                        addSuccessor(newmid, selector, next, false);
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
                // it generates a state that may not have successors (e.g. a
                // ReturnState) and then tries to put something after it.
                throw new IllegalArgumentException("Cannot splice into " +
                    "state graph after " + describeState(prev));
            }
            if (intransitive) {
                getStateInfo(prev).addIntransitiveSuccessor(next);
                getStateInfo(next).setPredecessor(selector, prev);
            }
        }

        private Grammar.Symbol symbolWithFlags(Grammar.Symbol base,
                                               int newFlags) {
            return (base == null) ? base : base.withFlags(newFlags);
        }

        @SuppressWarnings("fallthrough")
        protected State compileSymbol(Grammar.Symbol sym,
                                      Set<Grammar.Symbol> selectors)
                throws InvalidGrammarException {
            Grammar.Symbol cleanedSym = symbolWithFlags(sym,
                sym.getFlags() & ~COMPILER_FLAGS);
            if (sym instanceof Grammar.Nonterminal) {
                String cnt = ((Grammar.Nonterminal) sym).getReference();
                if (getToken(cnt) == null) {
                    for (Grammar.Symbol s : findInitialSymbols(cnt)) {
                        selectors.add(symbolWithFlags(s,
                            cleanedSym.getFlags()));
                    }
                    addProductions(cnt);
                    return createCallState((Grammar.Nonterminal) cleanedSym);
                }
            }
            selectors.add(cleanedSym);
            return createLiteralState(cleanedSym);
        }

        protected void addProduction(Grammar.Production prod)
                throws InvalidGrammarException {
            /* Prepare data structures. */
            Set<Grammar.Symbol> selectors =
                new LinkedHashSet<Grammar.Symbol>();
            State lastPrev = getInitialState(prod.getName());
            IdentityLinkedSet<State> prevs = new IdentityLinkedSet<State>();
            prevs.add(lastPrev);
            State tailJump = null;
            /* Iterate over the symbols! */
            List<Grammar.Symbol> syms = prod.getSymbols();
            int symCount = syms.size();
            for (int i = 0; i < symCount; i++) {
                Grammar.Symbol sym = syms.get(i);
                /* Create a state corresponding to the symbol. */
                State next = compileSymbol(sym, selectors);
                /* Tail recursion optimization. */
                if (i == symCount - 1 &&
                        sym instanceof Grammar.Nonterminal &&
                        ((Grammar.Nonterminal) sym).getReference().equals(
                            prod.getName()) &&
                        (sym.getFlags() & (Grammar.Symbol.SYM_INLINE |
                                           Grammar.Symbol.SYM_DISCARD |
                                           Grammar.Symbol.SYM_REPEAT)
                        ) == Grammar.Symbol.SYM_INLINE) {
                    next = createNullState();
                    addSuccessor(next, null, getInitialState(prod.getName()),
                                 true);
                    tailJump = next;
                }
                /* Special case: The production is a token definition.
                 * This could happen if the start symbol itself is a token
                 * definition. */
                if (getToken(prod.getName()) != null) {
                    Grammar.Symbol ref = new Grammar.Nonterminal(
                        prod.getName(), 0);
                    next = createLiteralState(ref);
                    selectors.clear();
                    selectors.add(ref);
                }
                /* Determine preliminarily whether this symbol could be
                 * skipped. */
                boolean maybeEmpty = (selectors.contains(null) ||
                    (sym.getFlags() & Grammar.Symbol.SYM_OPTIONAL) != 0);
                selectors.remove(null);
                /* Check if we can substitute an already-existing state
                 * instead. */
                for (Grammar.Symbol sel : selectors) {
                    State st = getSuccessor(lastPrev, sel, true);
                    // The mutual comparisons ensure that both next and st
                    // can veto the "merge".
                    if (next.matches(st) && st.matches(next)) {
                        next = st;
                        break;
                    }
                }
                /* Handle SYM_REPEAT. */
                if ((sym.getFlags() & Grammar.Symbol.SYM_REPEAT) != 0)
                    prevs.addLast(next);
                /* Add the new state graph edges.
                 * This will error out if there is any conflict. */
                for (State pr : prevs) {
                    boolean intransitive = (pr == lastPrev);
                    for (Grammar.Symbol sel : selectors) {
                        if (getSuccessor(pr, sel, false) != next)
                            addSuccessor(pr, sel, next, intransitive);
                    }
                }
                /* Prepare for the next iteration.
                 * We move next to the front of prevs to ensure it is chosen
                 * as the designated predecessor for the its successors'
                 * StateInfo. */
                if (maybeEmpty) {
                    prevs.remove(next);
                } else {
                    prevs.clear();
                }
                prevs.addFirst(next);
                selectors.clear();
                lastPrev = next;
            }
            /* Link the end(s) to the end state. */
            State next = getFinalState(prod.getName());
            for (State pr : prevs) {
                if (pr != tailJump && getSuccessor(pr, null, false) != next)
                    addSuccessor(pr, null, next, true);
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
                addProductions(Grammar.START_SYMBOL.getReference());
                startState = createCallState(Grammar.START_SYMBOL);
                addSuccessor(startState, null, createEndState(), true);
            }
            return startState;
        }

        protected void makeLexerStates(State st, Set<State> seen)
                throws InvalidGrammarException {
            if (seen.contains(st)) return;
            seen.add(st);
            if (st instanceof ExpectationSet) {
                ExpectationSet est = (ExpectationSet) st;
                est.setLexerState(getSelection(est.getExpectedTokens()));
            }
            for (State s : getAllSuccessors(st)) {
                makeLexerStates(s, seen);
            }
        }

        public State call() throws InvalidGrammarException {
            State ret = getStartState();
            makeLexerStates(ret, new HashSet<State>());
            return ret;
        }

    }

    /* Symbol flags that are handled by the parser compiler and thus need not
     * be considered for certain equivalence tests. */
    public static final int COMPILER_FLAGS = Grammar.Symbol.SYM_OPTIONAL |
                                             Grammar.Symbol.SYM_REPEAT;

    private final CompiledGrammar grammar;
    private final TokenSource source;
    private final boolean keepAll;
    private final List<ExpectationSet> expectations;
    private final List<ParseTreeImpl> treeStack;
    private final List<State> stateStack;
    private final Status status;
    private State state;
    private ParseTree result;

    public Parser(CompiledGrammar grammar, TokenSource source,
                  boolean keepAll) {
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

    protected TokenSource getTokenSource() {
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
                "Exception while closing token source: " + exc, exc);
        }
        if (getStateStack().size() != 0 || getTreeStack().size() != 1 ||
                getTreeStack().get(0).childCount() != 1)
            throw new IllegalStateException(
                "Internal parser state corrupted!");
        result = getTreeStack().remove(0).childAt(0);
        return result;
    }

    public static CompiledGrammar compile(GrammarView g)
            throws InvalidGrammarException {
        Compiler comp = new Compiler(g);
        return new CompiledGrammar(comp.getGrammar(), comp.call());
    }

}
