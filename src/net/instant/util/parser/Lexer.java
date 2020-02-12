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
import java.util.regex.MatchResult;
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
                   getPattern().equals(to.getPattern());
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

    // I wonder if there is a more elegant way of constructing this.
    private static final Pattern MATCH_NOTHING = Pattern.compile("[0&&1]");

    private final CompiledGrammar grammar;
    private final LineColumnReader input;
    private final StringBuilder inputBuffer;
    private final LineColumnReader.CoordinatesTracker inputPosition;
    private final List<Matcher> matchers;
    private State state;
    private MatcherListState matchersState;
    private boolean atEOF;
    private Token outputBuffer;

    public Lexer(CompiledGrammar grammar, LineColumnReader input) {
        this.grammar = grammar;
        this.input = input;
        this.inputBuffer = new StringBuilder();
        this.inputPosition = new LineColumnReader.CoordinatesTracker();
        this.matchers = new ArrayList<Matcher>();
        this.state = grammar.getInitialState();
        this.matchersState = MatcherListState.NEED_REBUILD;
        this.atEOF = false;
        this.outputBuffer = null;
    }

    protected CompiledGrammar getGrammar() {
        return grammar;
    }

    protected Reader getInput() {
        return input;
    }

    public LineColumnReader.Coordinates getPosition() {
        return inputPosition;
    }
    protected LineColumnReader.Coordinates copyPosition() {
        return new LineColumnReader.FixedCoordinates(inputPosition);
    }

    private void setMatchersState(MatcherListState st) {
        if (st.ordinal() > matchersState.ordinal())
            matchersState = st;
    }

    protected int pullInput() throws IOException {
        char[] data = new char[BUFFER_SIZE];
        int ret = input.read(data);
        if (ret < 0) return ret;
        inputBuffer.append(data, 0, ret);
        setMatchersState(MatcherListState.NEED_RESET);
        return ret;
    }
    protected int doMatch() {
        if (state == null) return -1;
        /* Prepare the matchers. */
        List<TokenPattern> patterns = state.getPatterns();
        switch (matchersState) {
            case NEED_REBUILD:
                int updateSize = Math.min(matchers.size(), patterns.size());
                while (matchers.size() < patterns.size()) {
                    Matcher m = patterns.get(matchers.size()).getPattern()
                        .matcher(inputBuffer);
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
                    m.reset(inputBuffer);
                }
                break;
        }
        matchersState = MatcherListState.READY;
        /* Apply them! */
        int matchIndex = -1, matchSize = Integer.MIN_VALUE;
        for (int i = 0; i < matchers.size(); i++) {
            Matcher m = matchers.get(i);
            boolean matched = m.lookingAt();
            if (m.hitEnd() && ! atEOF) return -1;
            if (! matched) continue;
            int thisMatchSize = m.end();
            if (thisMatchSize > matchSize) {
                matchIndex = i;
                matchSize = thisMatchSize;
            }
        }
        return matchIndex;
    }
    protected Token consumeInput(int length, int index) {
        String tokenContent = inputBuffer.substring(0, length);
        inputBuffer.delete(0, length);
        setMatchersState(MatcherListState.NEED_RESET);
        Token ret = new Token(copyPosition(),
            state.getPatterns().get(index).getName(), tokenContent);
        inputPosition.advance(tokenContent, 0, length);
        return ret;
    }
    protected void advance(int index) {
        State oldState = state;
        state = state.getSuccessors().get(
            state.getPatterns().get(index).getName());
        if (state != oldState)
            setMatchersState(MatcherListState.NEED_REBUILD);
    }

    public Token peek() throws IOException, LexingException {
        if (outputBuffer != null)
            return outputBuffer;
        for (;;) {
            int matchIndex = doMatch();
            if (matchIndex != -1) {
                outputBuffer = consumeInput(matchers.get(matchIndex).end(),
                                            matchIndex);
                advance(matchIndex);
                return outputBuffer;
            } else if (atEOF) {
                if (state != null && ! state.isAccepting()) {
                    // If there is any unconsumed input, we can as well blame
                    // its first character.
                    String message = (inputBuffer.length() == 0) ?
                        "Unexpected end of input" :
                        "Unexpected character " + Formats.formatCharacter(
                            Character.codePointAt(inputBuffer, 0));
                    LineColumnReader.Coordinates pos = copyPosition();
                    throw new LexingException(pos, message + " at " + pos);
                } else if (inputBuffer.length() == 0) {
                    state = null;
                    return null;
                } else {
                    LineColumnReader.Coordinates pos = copyPosition();
                    throw new LexingException(pos, "Unconsumed input at " +
                                              pos);
                }
            } else if (pullInput() == -1) {
                atEOF = true;
            }
        }
    }

    public Token read() throws IOException, LexingException {
        if (outputBuffer == null) peek();
        Token ret = outputBuffer;
        outputBuffer = null;
        return ret;
    }

    public void close() throws IOException {
        input.close();
        inputBuffer.setLength(0);
        matchers.clear();
        state = null;
        matchersState = MatcherListState.NEED_REBUILD;
        atEOF = true;
        outputBuffer = null;
    }

    public static CompiledGrammar compile(LexerGrammar g)
            throws InvalidGrammarException {
        Compiler comp = new Compiler(g);
        return new CompiledGrammar(comp.getGrammar(), comp.call());
    }

    public static int chooseMatchedGroup(MatchResult res) {
        int maxIdx = -1, maxSize = -1;
        for (int i = 1; i <= res.groupCount(); i++) {
            if (res.start(i) == -1) continue;
            int size = res.end(i) - res.start(i);
            if (size <= maxSize) continue;
            maxIdx = i;
            maxSize = size;
        }
        return maxIdx;
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

}
