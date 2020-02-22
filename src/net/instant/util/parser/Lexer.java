package net.instant.util.parser;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.instant.util.Formats;
import net.instant.util.LineColumnReader;
import net.instant.util.NamedMap;
import net.instant.util.NamedSet;
import net.instant.util.NamedValue;

public class Lexer implements Closeable {

    public static class LexerGrammar extends Grammar {

        public static final Symbol START_SYMBOL =
            Symbol.nonterminal("$tokens", 0);

        public LexerGrammar() {
            super();
        }
        public LexerGrammar(GrammarView copyFrom) {
            super(copyFrom);
        }
        public LexerGrammar(Production... productions) {
            super(productions);
        }

        protected void validate(String startSymbol)
                throws InvalidGrammarException {
            super.validate(startSymbol);
            for (NamedSet<Production> ps : getRawProductions().values()) {
                for (Production pr : ps) {
                    List<Symbol> syms = pr.getSymbols();
                    int size = syms.size();
                    if (size > 2)
                        throw new InvalidGrammarException("Too many " +
                            "symbols in LexerGrammar production");
                    if (size > 1 &&
                            syms.get(1).getType() != SymbolType.NONTERMINAL)
                        throw new InvalidGrammarException("LexerGrammar " +
                            "second production symbols must be nonterminals");
                    for (Symbol s : syms) {
                        if ((s.getFlags() & SYM_ALL) != 0)
                            throw new InvalidGrammarException(
                                "LexerGrammar symbols must have no flags");
                    }
                }
            }
        }
        public void validate() throws InvalidGrammarException {
            validate(START_SYMBOL.getContent());
        }

    }

    public static class CompiledGrammar implements GrammarView {

        private final LexerGrammar source;
        private final State initialState;

        protected CompiledGrammar(LexerGrammar source, State initialState) {
            this.source = source;
            this.initialState = initialState;
        }

        protected GrammarView getSource() {
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

        public Lexer makeLexer(LineColumnReader input) {
            return new Lexer(this, input);
        }
        public Lexer makeLexer(Reader input) {
            return makeLexer(new LineColumnReader(input));
        }

    }

    public static class Token {

        private final LineColumnReader.Coordinates position;
        private final String production;
        private final String content;

        public Token(LineColumnReader.Coordinates position,
                     String production, String content) {
            if (position == null)
                throw new NullPointerException(
                    "Token coordinates may not be null");
            if (content == null)
                throw new NullPointerException(
                    "Token content may not be null");
            this.position = position;
            this.production = production;
            this.content = content;
        }

        public String toString() {
            return String.format(
                "%s@%h[position=%s,production=%s,content=%s]",
                getClass().getName(), this, getPosition(), getProduction(),
                getContent());
        }
        public String toUserString() {
            String prod = getProduction();
            return String.format("%s%s at %s",
                Formats.formatString(getContent()),
                ((prod == null) ? "" : " (" + prod + ")"),
                getPosition());
        }

        public boolean equals(Object other) {
            if (! (other instanceof Token)) return false;
            Token to = (Token) other;
            return (getPosition().equals(to.getPosition()) &&
                    equalOrNull(getProduction(), to.getProduction()) &&
                    getContent().equals(to.getContent()));
        }

        public int hashCode() {
            return getPosition().hashCode() ^
                hashCodeOrNull(getProduction()) ^ getContent().hashCode();
        }

        public LineColumnReader.Coordinates getPosition() {
            return position;
        }

        public String getProduction() {
            return production;
        }

        public String getContent() {
            return content;
        }

        public boolean matches(Grammar.Symbol sym) {
            if (sym.getType() == Grammar.SymbolType.NONTERMINAL) {
                return sym.getContent().equals(getProduction());
            } else {
                return sym.getPattern().matcher(getContent()).matches();
            }
        }

        private static boolean equalOrNull(String a, String b) {
            return (a == null) ? (b == null) : a.equals(b);
        }
        private static int hashCodeOrNull(Object o) {
            return (o == null) ? 0 : o.hashCode();
        }

    }

    public static class LexingException extends LocatedParserException {

        public LexingException(LineColumnReader.Coordinates pos) {
            super(pos);
        }
        public LexingException(LineColumnReader.Coordinates pos,
                               String message) {
            super(pos, message);
        }
        public LexingException(LineColumnReader.Coordinates pos,
                               Throwable cause) {
            super(pos, cause);
        }
        public LexingException(LineColumnReader.Coordinates pos,
                               String message, Throwable cause) {
            super(pos, message, cause);
        }

    }

    protected static class TokenPattern implements NamedValue {

        private final String name;
        private final Grammar.SymbolType type;
        private final Pattern pattern;

        public TokenPattern(String name, Grammar.SymbolType type,
                            Pattern pattern) {
            if (name == null)
                throw new NullPointerException(
                    "TokenPattern name may not be null");
            if (type == null)
                throw new NullPointerException(
                    "TokenPattern type may not be null");
            if (pattern == null)
                throw new NullPointerException(
                    "TokenPattern pattern may not be null");
            this.name = name;
            this.type = type;
            this.pattern = pattern;
        }

        public String toString() {
            return String.format("%s@%h[name=%s,type=%s,pattern=%s]",
                                 getClass().getName(), this, getName(),
                                 getType(), getPattern());
        }

        public boolean equals(Object other) {
            if (! (other instanceof TokenPattern)) return false;
            TokenPattern to = (TokenPattern) other;
            return getName().equals(to.getName()) &&
                   getType().equals(to.getType()) &&
                   patternsEqual(getPattern(), to.getPattern());
        }

        public int hashCode() {
            return getName().hashCode() ^ getType().hashCode() ^
                patternHashCode(getPattern());
        }

        public String getName() {
            return name;
        }

        public Grammar.SymbolType getType() {
            return type;
        }

        public Pattern getPattern() {
            return pattern;
        }

    }

    protected interface State extends NamedValue {

        List<TokenPattern> getPatterns();

        TokenPattern getPattern(int index);

        Map<String, State> getSuccessors();

        boolean isAccepting();

    }

    protected static class FinalState implements State {

        private final String name;
        private final List<TokenPattern> patterns;
        private final Map<String, State> successors;
        private final boolean accepting;

        public FinalState(String name, List<TokenPattern> patterns,
                Map<String, State> successors, boolean accepting) {
            this.name = name;
            this.patterns = patterns;
            this.successors = successors;
            this.accepting = accepting;
        }

        public String getName() {
            return name;
        }

        public List<TokenPattern> getPatterns() {
            return patterns;
        }

        public TokenPattern getPattern(int index) {
            return patterns.get(index);
        }

        public Map<String, State> getSuccessors() {
            return successors;
        }

        public boolean isAccepting() {
            return accepting;
        }

    }

    protected static class StateBuilder implements State {

        private final String name;
        private final Set<TokenPattern> patternSet;
        private final Map<String, State> successors;
        private boolean accepting;

        public StateBuilder(String name) {
            this.name = name;
            this.patternSet = new LinkedHashSet<TokenPattern>();
            this.successors = new LinkedHashMap<String, State>();
            this.accepting = false;
        }

        public String getName() {
            return name;
        }

        public List<TokenPattern> getPatterns() {
            return Collections.unmodifiableList(
                new ArrayList<TokenPattern>(patternSet));
        }
        public Set<TokenPattern> getPatternSet() {
            return patternSet;
        }

        public TokenPattern getPattern(int index) {
            int i = 0;
            for (TokenPattern pat : patternSet) {
                if (i++ == index) return pat;
            }
            throw new IndexOutOfBoundsException("No pattern at index " +
                                                index);
        }

        public Map<String, State> getSuccessors() {
            return successors;
        }

        public boolean isAccepting() {
            return accepting;
        }
        public void setAccepting(boolean a) {
            accepting = a;
        }

    }

    protected static class Compiler implements Callable<State> {

        private final LexerGrammar grammar;
        private final Map<String, StateBuilder> states;
        private final Set<String> seenStates;

        public Compiler(LexerGrammar grammar) throws InvalidGrammarException {
            this.grammar = new LexerGrammar(grammar);
            this.states = new NamedMap<StateBuilder>();
            this.seenStates = new HashSet<String>();
            this.grammar.validate();
        }

        protected LexerGrammar getGrammar() {
            return grammar;
        }

        protected StateBuilder getStateBuilder(String name) {
            StateBuilder ret = states.get(name);
            if (ret == null) {
                ret = new StateBuilder(name);
                states.put(name, ret);
            }
            return ret;
        }

        protected void compileProduction(String state, String name,
                Grammar.Production pr) throws InvalidGrammarException {
            if (pr.getSymbols().size() != 1)
                throw new InvalidGrammarException("Lexer token " + name +
                    " definitions must contain exactly one nonterminal " +
                    "each");
            Grammar.Symbol sym = pr.getSymbols().get(0);
            if (sym.getType() == Grammar.SymbolType.NONTERMINAL)
                throw new InvalidGrammarException("Lexer token " + name +
                    " definition may not contain nonterminals");
            getStateBuilder(state).getPatternSet().add(
                new TokenPattern(name, sym.getType(), sym.getPattern()));
        }

        protected void compileTerminals(String state, String name,
                String nextStateName) throws InvalidGrammarException {
            getStateBuilder(state).getSuccessors().put(name,
                (nextStateName == null) ?
                    null :
                    getStateBuilder(nextStateName));
            for (Grammar.Production pr : grammar.getProductions(name)) {
                compileProduction(state, name, pr);
            }
        }

        @SuppressWarnings("fallthrough")
        protected StateBuilder compileState(String state)
                throws InvalidGrammarException {
            StateBuilder st = getStateBuilder(state);
            if (seenStates.contains(state)) return st;
            seenStates.add(state);
            for (Grammar.Production pr : grammar.getProductions(state)) {
                List<Grammar.Symbol> syms = pr.getSymbols();
                String nextStateName = null;
                switch (syms.size()) {
                    case 0:
                        st.setAccepting(true);
                        break;
                    case 2:
                        // Index-1 symbols are validated to be nonterminals.
                        nextStateName = syms.get(1).getContent();
                        compileState(nextStateName);
                    case 1:
                        Grammar.Symbol sym = syms.get(0);
                        if (sym.getType() != Grammar.SymbolType.NONTERMINAL)
                            throw new InvalidGrammarException("Lexer " +
                                "grammar state productions may only " +
                                "contain nonterminals");
                        compileTerminals(state, syms.get(0).getContent(),
                                         nextStateName);
                        break;
                }
            }
            return st;
        }

        protected State freeze(State base, Map<String, State> memo,
                Map<String, Map<String, State>> successorMemo) {
            if (base == null) return null;
            State ret = memo.get(base.getName());
            if (ret == null) {
                Map<String, State> successors =
                    new LinkedHashMap<String, State>();
                ret = new FinalState(base.getName(), base.getPatterns(),
                    Collections.unmodifiableMap(successors),
                    base.isAccepting());
                memo.put(ret.getName(), ret);
                successorMemo.put(ret.getName(), successors);
                for (Map.Entry<String, State> ent :
                     base.getSuccessors().entrySet()) {
                    successors.put(ent.getKey(), freeze(ent.getValue(), memo,
                                                        successorMemo));
                }
            }
            return ret;
        }

        public State call() throws InvalidGrammarException {
            return freeze(
                compileState(LexerGrammar.START_SYMBOL.getContent()),
                new HashMap<String, State>(),
                new HashMap<String, Map<String, State>>());
        }

    }

    private enum MatcherListState { READY, NEED_RESET, NEED_REBUILD }

    private static final int BUFFER_SIZE = 8192;

    private final CompiledGrammar grammar;
    private final LineColumnReader input;
    private final StringBuilder inputBuffer;
    private final LineColumnReader.CoordinatesTracker inputPosition;
    private final List<Matcher> matchers;
    private State state;
    private boolean atEOF;
    private Token outputBuffer;
    private MatcherListState matchersState;

    public Lexer(CompiledGrammar grammar, LineColumnReader input) {
        this.grammar = grammar;
        this.input = input;
        this.inputBuffer = new StringBuilder();
        this.inputPosition = new LineColumnReader.CoordinatesTracker();
        this.matchers = new ArrayList<Matcher>();
        this.state = grammar.getInitialState();
        this.atEOF = false;
        this.outputBuffer = null;
        this.matchersState = MatcherListState.NEED_REBUILD;
    }

    protected CompiledGrammar getGrammar() {
        return grammar;
    }

    protected Reader getInput() {
        return input;
    }

    protected StringBuilder getInputBuffer() {
        return inputBuffer;
    }

    public LineColumnReader.Coordinates getInputPosition() {
        return new LineColumnReader.FixedCoordinates(inputPosition);
    }
    protected LineColumnReader.CoordinatesTracker getRawPosition() {
        return inputPosition;
    }

    protected List<Matcher> getMatchers() {
        List<TokenPattern> patterns = getState().getPatterns();
        StringBuilder buf = getInputBuffer();
        switch (matchersState) {
            case NEED_REBUILD:
                int updateSize = Math.min(matchers.size(), patterns.size());
                while (matchers.size() < patterns.size()) {
                    Matcher m = patterns.get(matchers.size()).getPattern()
                        .matcher(buf);
                    m.useAnchoringBounds(false);
                    matchers.add(m);
                }
                if (matchers.size() > patterns.size()) {
                    matchers.subList(patterns.size(), matchers.size())
                        .clear();
                }
                for (int i = 0; i < updateSize; i++) {
                    matchers.get(i).usePattern(patterns.get(i).getPattern());
                }
                break;
            case NEED_RESET:
                for (Matcher m : matchers) {
                    m.reset(buf);
                }
                break;
        }
        matchersState = MatcherListState.READY;
        return matchers;
    }

    protected State getState() {
        return state;
    }
    protected void setState(State s) {
        if (s == state) return;
        state = s;
        setMatchersState(MatcherListState.NEED_REBUILD);
    }

    protected boolean isAtEOF() {
        return atEOF;
    }
    protected void setAtEOF(boolean v) {
        atEOF = v;
    }

    protected Token getOutputBuffer() {
        return outputBuffer;
    }
    protected void setOutputBuffer(Token t) {
        outputBuffer = t;
    }

    private void setMatchersState(MatcherListState st) {
        if (st.ordinal() > matchersState.ordinal())
            matchersState = st;
    }

    protected int pullInput() throws LexingException {
        return pullInput(BUFFER_SIZE);
    }
    protected int pullInput(int size) throws LexingException {
        char[] data = new char[size];
        int ret;
        try {
            ret = getInput().read(data);
        } catch (IOException exc) {
            throw new LexingException(getInputPosition(), exc);
        }
        if (ret < 0) {
            setAtEOF(true);
            return ret;
        }
        getInputBuffer().append(data, 0, ret);
        setMatchersState(MatcherListState.NEED_RESET);
        return ret;
    }
    protected Token consumeInput(int length, int index) {
        String tokenContent = getInputBuffer().substring(0, length);
        getInputBuffer().delete(0, length);
        setMatchersState(MatcherListState.NEED_RESET);
        Token ret = new Token(getInputPosition(),
            getState().getPatterns().get(index).getName(), tokenContent);
        getRawPosition().advance(tokenContent, 0, length);
        return ret;
    }
    protected void advance(int index) {
        State state = getState();
        setState(state.getSuccessors().get(
            state.getPatterns().get(index).getName()));
    }

    protected Token doMatch() throws LexingException {
        if (getState() == null) return null;
        List<TokenPattern> patterns = getState().getPatterns();
        List<Matcher> matchers = getMatchers();
        int bestIndex = -1;
        int bestSize = Integer.MIN_VALUE;
        int bestRank = Integer.MAX_VALUE;
        for (int i = 0; i < matchers.size(); i++) {
            Matcher m = matchers.get(i);
            boolean matched = m.lookingAt();
            if (m.hitEnd() && ! isAtEOF()) return null;
            if (! matched) continue;
            int thisSize = m.end();
            int thisRank = patterns.get(i).getType().ordinal();
            if (thisSize < bestSize ||
                    (thisSize == bestSize && thisRank > bestRank)) {
                continue;
            } else if (thisSize == bestSize && thisRank == bestRank) {
                throw new LexingException(getInputPosition(), "Ambiguous " +
                    "classifications for prospective token " +
                    Formats.formatString(m.group()) + " at " +
                    getInputPosition() + ": " +
                    patterns.get(bestIndex).getName() + " and " +
                    patterns.get(i).getName());
            }
            bestIndex = i;
            bestSize = thisSize;
            bestRank = thisRank;
        }
        if (bestIndex == -1) return null;
        Token ret = consumeInput(matchers.get(bestIndex).end(), bestIndex);
        advance(bestIndex);
        return ret;
    }

    private LexingException unexpectedInput() {
        LineColumnReader.Coordinates pos = getInputPosition();
        // If there is any unexpected input, we can as well blame its first
        // character (perhaps it is the *reason* the input is unexpected)?
        String message = (getInputBuffer().length() == 0) ?
            "Unexpected end of input" :
            "Unexpected character " + Formats.formatCharacter(
                Character.codePointAt(getInputBuffer(), 0));
        return new LexingException(pos, message + " at " + pos);
    }

    public Token peek() throws LexingException {
        Token tok = getOutputBuffer();
        if (tok != null)
            return tok;
        for (;;) {
            tok = doMatch();
            if (tok != null) {
                setOutputBuffer(tok);
                return tok;
            } else if (isAtEOF()) {
                if (getState() != null && ! getState().isAccepting() ||
                        getInputBuffer().length() != 0)
                    throw unexpectedInput();
                setState(null);
                return null;
            } else {
                pullInput();
            }
        }
    }

    public Token read() throws LexingException {
        Token ret = getOutputBuffer();
        if (ret == null) ret = peek();
        setOutputBuffer(null);
        return ret;
    }

    public void close() throws IOException {
        input.close();
        inputBuffer.setLength(0);
        matchers.clear();
        state = null;
        atEOF = true;
        outputBuffer = null;
        matchersState = MatcherListState.NEED_REBUILD;
    }

    public static Grammar.Production terminalToken(String name,
                                                   String content) {
        return new Grammar.Production(name, Grammar.Symbol.terminal(content));
    }
    public static Grammar.Production patternToken(String name,
                                                  Pattern content) {
        return new Grammar.Production(name, Grammar.Symbol.pattern(content));
    }
    public static Grammar.Production patternToken(String name,
                                                  String content) {
        return new Grammar.Production(name, Grammar.Symbol.pattern(content));
    }
    public static Grammar.Production anythingToken(String name) {
        return new Grammar.Production(name, Grammar.Symbol.anything());
    }

    public static Grammar.Production state(String name) {
        return new Grammar.Production(name);
    }
    public static Grammar.Production state(String name, String token) {
        return new Grammar.Production(name,
                                      Grammar.Symbol.nonterminal(token));
    }
    public static Grammar.Production state(String name, String token,
                                           String next) {
        return new Grammar.Production(name,
                                      Grammar.Symbol.nonterminal(token),
                                      Grammar.Symbol.nonterminal(next));
    }

    public static CompiledGrammar compile(LexerGrammar g)
            throws InvalidGrammarException {
        Compiler comp = new Compiler(g);
        return new CompiledGrammar(comp.getGrammar(), comp.call());
    }

    public static boolean patternsEqual(Pattern a, Pattern b) {
        // HACK: Assuming the Pattern API does not change in incompatible
        //       ways...
        if (a == null) return (b == null);
        if (b == null) return (a == null);
        return (a.pattern().equals(b.pattern()) &&
                a.flags() == b.flags());
    }

    public static int patternHashCode(Pattern pat) {
        if (pat == null) return 0;
        return pat.pattern().hashCode() ^ pat.flags();
    }

}
