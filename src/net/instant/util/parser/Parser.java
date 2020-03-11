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
import java.util.regex.Pattern;
import net.instant.util.IdentityLinkedSet;
import net.instant.util.LineColumnReader;
import net.instant.util.NamedSet;
import net.instant.util.NamedValue;

public class Parser {

    public static class ParserGrammar extends Grammar {

        public static final Symbol START_SYMBOL =
            Symbol.nonterminal("$start", 0);

        public ParserGrammar() {
            super();
        }
        public ParserGrammar(GrammarView copyFrom) {
            super(copyFrom);
        }
        public ParserGrammar(List<Production> productions) {
            super(productions);
        }
        public ParserGrammar(Production... productions) {
            super(productions);
        }

        public void validate() throws InvalidGrammarException {
            validate(START_SYMBOL.getContent());
        }

        public static Grammar.Production terminalToken(String name,
                                                       String content) {
            return new Grammar.Production(name,
                Grammar.Symbol.terminal(content, Grammar.SYM_INLINE));
        }
        public static Grammar.Production patternToken(String name,
                                                      Pattern content) {
            return new Grammar.Production(name,
                Grammar.Symbol.pattern(content, Grammar.SYM_INLINE));
        }
        public static Grammar.Production patternToken(String name,
                                                      String content) {
            return new Grammar.Production(name,
                Grammar.Symbol.pattern(content, Grammar.SYM_INLINE));
        }
        public static Grammar.Production anythingToken(String name) {
            return new Grammar.Production(name,
                Grammar.Symbol.anything(Grammar.SYM_INLINE));
        }

    }

    public static class CompiledGrammar implements GrammarView {

        private final ParserGrammar source;
        private final State initialState;

        protected CompiledGrammar(ParserGrammar source, State initialState) {
            this.source = source;
            this.initialState = initialState;
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

        protected Lexer makeLexer(LineColumnReader input) {
            return new Lexer(input);
        }

        public Parser makeParser(LineColumnReader input, boolean keepAll) {
            return new Parser(this, makeLexer(input), keepAll);
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

        Lexer.MatchStatus getCurrentTokenStatus() throws ParsingException;

        Lexer.Token getCurrentToken(boolean required) throws ParsingException;

        void nextToken() throws ParsingException;

        void storeToken(Lexer.Token tok);

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

        Lexer.State getLexerState();

        void setLexerState(Lexer.State st);

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

        private ParsingException wrap(Lexer.LexingException exc) {
            return new ParsingException(exc.getPosition(), exc.getMessage(),
                                        exc);
        }

        public boolean isKeepingAll() {
            return Parser.this.isKeepingAll();
        }

        public LineColumnReader.Coordinates getCurrentPosition() {
            return getLexer().getInputPosition();
        }

        public Lexer.MatchStatus getCurrentTokenStatus()
                throws ParsingException {
            try {
                return getLexer().peek();
            } catch (Lexer.LexingException exc) {
                throw wrap(exc);
            }
        }

        public Lexer.Token getCurrentToken(boolean required)
                throws ParsingException {
            try {
                Lexer.MatchStatus st = getLexer().peek();
                if (st == Lexer.MatchStatus.OK) {
                    return getLexer().getToken();
                } else if (! required || st == Lexer.MatchStatus.EOI) {
                    return null;
                } else {
                    throw getLexer().unexpectedInput();
                }
            } catch (Lexer.LexingException exc) {
                throw wrap(exc);
            }
        }

        public void nextToken() throws ParsingException {
            try {
                getLexer().next();
            } catch (Lexer.LexingException exc) {
                throw wrap(exc);
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

        public void setExpectation(ExpectationSet exp) {
            getExpectations().add(exp);
            getLexer().setState(exp.getLexerState());
        }

        public String formatExpectations() {
            Set<String> accum = new LinkedHashSet<String>();
            for (ExpectationSet exp : getExpectations()) {
                for (Grammar.Symbol sym : exp.getExpectedTokens()) {
                    if (sym == null) {
                        accum.add("end of input");
                    } else if (sym.getType() == Grammar.SymbolType.ANYTHING) {
                        return "anything";
                    } else {
                        accum.add(sym.toUserString());
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
                tok = getCurrentToken(true);
            } catch (ParsingException exc) {
                // Good enough. The exception could, in particular, be an
                // "unexpected character" error, which we definitely want to
                // propagate.
                return exc;
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
            if (status.isKeepingAll()) flags &= ~Grammar.SYM_DISCARD;
            status.pushState(getSuccessor(), sym.getContent(), flags);
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
        private Lexer.State lexerState;

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

        public Lexer.State getLexerState() {
            return lexerState;
        }

        public void setLexerState(Lexer.State state) {
            lexerState = state;
        }

        public Set<Grammar.Symbol> getExpectedTokens() {
            return Collections.singleton(expected);
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
            status.setExpectation(this);
            Lexer.Token tok = status.getCurrentToken(true);
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
        private Lexer.State lexerState;

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

        public Lexer.State getLexerState() {
            return lexerState;
        }

        public void setLexerState(Lexer.State state) {
            lexerState = state;
        }

        public Set<Grammar.Symbol> getExpectedTokens() {
            Set<Grammar.Symbol> ret = new LinkedHashSet<Grammar.Symbol>();
            for (String key : successors.keySet()) {
                if (key == null) continue;
                ret.add(new Grammar.Symbol(Grammar.SymbolType.NONTERMINAL,
                                           key, 0));
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
            Lexer.Token tok = status.getCurrentToken(false);
            State succ = (tok == null) ? null : successors.get(tok.getName());
            if (succ == null)
                succ = successors.get(null);
            if (succ == null)
                throw status.unexpectedToken();
            status.setState(succ);
        }

    }

    protected static class EndState implements State, ExpectationSet {

        private Lexer.State lexerState;

        public Lexer.State getLexerState() {
            return lexerState;
        }

        public void setLexerState(Lexer.State state) {
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
        private final Map<String, Lexer.TokenPattern> tokens;
        private final Map<Set<Grammar.Symbol>, Lexer.State> lexerStates;
        private final Map<String, State> initialStates;
        private final Map<String, State> finalStates;
        private final Map<String, Set<Grammar.Symbol>> initialSymbolCache;
        private final Map<State, StateInfo> info;
        private State startState;

        public Compiler(ParserGrammar grammar)
                throws InvalidGrammarException {
            this.grammar = new ParserGrammar(grammar);
            this.seenProductions = new HashSet<String>();
            this.tokens = new HashMap<String, Lexer.TokenPattern>();
            this.lexerStates = new HashMap<Set<Grammar.Symbol>,
                                           Lexer.State>();
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

        protected Lexer.State createLexerState(
                Map<String, Lexer.TokenPattern> tokens) {
            return new Lexer.StandardState(tokens, true);
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
        protected EndState createEndState() {
            return new EndState();
        }
        protected StateInfo createStateInfo(State state) {
            return new StateInfo(state);
        }

        protected Lexer.TokenPattern compileToken(String name)
                throws InvalidGrammarException {
            Set<Grammar.Production> prods = grammar.getRawProductions(name);
            if (prods.size() != 1) return null;
            Grammar.Production prod = prods.iterator().next();
            if (prod.getSymbols().size() != 1) return null;
            Grammar.Symbol sym = prod.getSymbols().get(0);
            if (sym.getFlags() != Grammar.SYM_INLINE) return null;
            Lexer.TokenPattern ret = Lexer.TokenPattern.create(name, prods);
            return ret;
        }
        protected Lexer.TokenPattern getToken(String name)
                throws InvalidGrammarException {
            if (! tokens.containsKey(name))
                tokens.put(name, compileToken(name));
            return tokens.get(name);
        }

        protected Lexer.State getLexerState(Set<Grammar.Symbol> syms) {
            Lexer.State ret = lexerStates.get(syms);
            if (ret == null) {
                Map<String, Lexer.TokenPattern> tokens =
                    new LinkedHashMap<String, Lexer.TokenPattern>();
                for (Grammar.Symbol s : syms) {
                    if (s == null)
                        continue;
                    if (s.getType() != Grammar.SymbolType.NONTERMINAL)
                        throw new IllegalArgumentException(
                            "Token definition set contains a raw terminal");
                    Lexer.TokenPattern tok = null;
                    Throwable error = null;
                    try {
                        tok = getToken(s.getContent());
                    } catch (InvalidGrammarException exc) {
                        error = exc;
                    }
                    if (tok == null)
                        throw new IllegalStateException(
                            "Unrecognized token " + s.getContent() + "?!",
                            error);
                    tokens.put(tok.getName(), tok);
                }
                ret = createLexerState(tokens);
                lexerStates.put(new HashSet<Grammar.Symbol>(syms), ret);
            }
            return ret;
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
            if (base == null || base.getFlags() == newFlags) return base;
            return new Grammar.Symbol(base.getType(), base.getContent(),
                                      newFlags);
        }

        @SuppressWarnings("fallthrough")
        protected State compileSymbol(Grammar.Symbol sym,
                                      Set<Grammar.Symbol> selectors)
                throws InvalidGrammarException {
            Grammar.Symbol cleanedSym = symbolWithFlags(sym,
                sym.getFlags() & ~COMPILER_FLAGS);
            switch (sym.getType()) {
                case NONTERMINAL:
                    String c = sym.getContent();
                    if (getToken(c) == null) {
                        for (Grammar.Symbol s : findInitialSymbols(c)) {
                            selectors.add(symbolWithFlags(s,
                                cleanedSym.getFlags()));
                        }
                        addProductions(c);
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
                /* Create a state corresponding to the symbol; perform some
                 * initial data wrangling. */
                State next = compileSymbol(sym, selectors);
                if (i == symCount - 1 &&
                        sym.getType() == Grammar.SymbolType.NONTERMINAL &&
                        sym.getContent().equals(prod.getName()) &&
                        (sym.getFlags() & (Grammar.SYM_INLINE |
                                           Grammar.SYM_DISCARD |
                                           Grammar.SYM_REPEAT)
                        ) == Grammar.SYM_INLINE) {
                    /* Tail recursion optimization. */
                    next = createNullState();
                    addSuccessor(next, null, getInitialState(prod.getName()),
                                 true);
                    tailJump = next;
                }
                boolean maybeEmpty = (selectors.contains(null) ||
                    (sym.getFlags() & Grammar.SYM_OPTIONAL) != 0);
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
                if ((sym.getFlags() & Grammar.SYM_REPEAT) != 0)
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
                addProductions(ParserGrammar.START_SYMBOL.getContent());
                startState = createCallState(ParserGrammar.START_SYMBOL);
                addSuccessor(startState, null, createEndState(), true);
            }
            return startState;
        }

        protected void makeLexerStates(State st, Set<State> seen) {
            if (seen.contains(st)) return;
            seen.add(st);
            if (st instanceof ExpectationSet) {
                ExpectationSet est = (ExpectationSet) st;
                est.setLexerState(getLexerState(est.getExpectedTokens()));
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
    public static final int COMPILER_FLAGS = Grammar.SYM_OPTIONAL |
                                             Grammar.SYM_REPEAT;

    private final CompiledGrammar grammar;
    private final Lexer lexer;
    private final boolean keepAll;
    private final List<ExpectationSet> expectations;
    private final List<ParseTreeImpl> treeStack;
    private final List<State> stateStack;
    private final Status status;
    private State state;
    private ParseTree result;

    public Parser(CompiledGrammar grammar, Lexer lexer, boolean keepAll) {
        this.grammar = grammar;
        this.lexer = lexer;
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

    protected Lexer getLexer() {
        return lexer;
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
            lexer.close();
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
        return new CompiledGrammar(comp.getGrammar(), comp.call());
    }

}
