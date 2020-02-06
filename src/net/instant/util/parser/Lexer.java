package net.instant.util.parser;

import java.io.Closeable;
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

    protected interface State extends NamedValue {

        Pattern getPattern();

        List<String> getGroupNames();

        List<State> getSuccessors();

        boolean isAccepting();

    }

    protected static class FinalState implements State {

        private final String name;
        private final Pattern pattern;
        private final List<String> groupNames;
        private final List<State> successors;
        private final boolean accepting;

        public FinalState(String name, Pattern pattern,
                List<String> groupNames, List<State> successors,
                boolean accepting) {
            this.name = name;
            this.pattern = pattern;
            this.groupNames = groupNames;
            this.successors = successors;
            this.accepting = accepting;
        }

        public String getName() {
            return name;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public List<String> getGroupNames() {
            return groupNames;
        }

        public List<State> getSuccessors() {
            return successors;
        }

        public boolean isAccepting() {
            return accepting;
        }

    }

    protected static class StateBuilder implements State {

        private final String name;
        private final StringBuilder patternBuilder;
        private final List<String> groupNames;
        private final List<State> successors;
        private boolean accepting;

        public StateBuilder(String name) {
            this.name = name;
            this.patternBuilder = new StringBuilder();
            this.groupNames = new ArrayList<String>();
            this.successors = new ArrayList<State>();
            this.accepting = false;
        }

        public String getName() {
            return name;
        }

        public Pattern getPattern() {
            return Pattern.compile(patternBuilder.toString());
        }
        public StringBuilder getPatternBuilder() {
            return patternBuilder;
        }

        public List<String> getGroupNames() {
            return groupNames;
        }

        public List<State> getSuccessors() {
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
            StateBuilder st = getStateBuilder(state);
            if (pr.getSymbols().size() != 1)
                throw new InvalidGrammarException("Lexer token " + name +
                    " definition must contain exactly one nonterminal " +
                    "each");
            Grammar.Symbol sym = pr.getSymbols().get(0);
            if (sym.getType() == Grammar.SymbolType.NONTERMINAL)
                throw new InvalidGrammarException("Lexer token " + name +
                    " definition may not contain nonterminals");
            Pattern sympat = sym.getPattern();
            // HACK: Pattern does not allow querying the group count, we need
            //       to create a dummy Matcher instead.
            if (sympat.matcher("").groupCount() != 0)
                throw new InvalidGrammarException("Lexer token " + name +
                    " has capturing groups.");
            st.getPatternBuilder().append("(?:").append(sympat.pattern())
                                  .append(')');
        }

        protected void compileTerminal(String state, String name,
                String nextStateName) throws InvalidGrammarException {
            StateBuilder st = getStateBuilder(state);
            StringBuilder pattern = st.getPatternBuilder();
            pattern.append('(');
            st.getGroupNames().add(name);
            st.getSuccessors().add((nextStateName == null) ? null :
                getStateBuilder(nextStateName));
            boolean first = true;
            for (Grammar.Production pr : grammar.getProductions(name)) {
                if (first) {
                    first = false;
                } else {
                    pattern.append('|');
                }
                compileProduction(state, name, pr);
            }
            pattern.append(')');
        }

        @SuppressWarnings("fallthrough")
        protected StateBuilder compileState(String state)
                throws InvalidGrammarException {
            StateBuilder st = getStateBuilder(state);
            if (seenStates.contains(state)) return st;
            seenStates.add(state);
            boolean firstTerminal = true;
            for (Grammar.Production pr : grammar.getProductions(state)) {
                List<Grammar.Symbol> syms = pr.getSymbols();
                String nextState = null;
                switch (syms.size()) {
                    case 0:
                        st.setAccepting(true);
                        break;
                    case 2:
                        // Index-1 symbols are validated to be nonterminals.
                        nextState = syms.get(1).getContent();
                        compileState(nextState);
                    case 1:
                        Grammar.Symbol sym = syms.get(0);
                        if (sym.getType() != Grammar.SymbolType.NONTERMINAL)
                            throw new InvalidGrammarException("Lexer " +
                                "grammar state productions may only " +
                                "contain nonterminals");
                        if (firstTerminal) {
                            firstTerminal = false;
                        } else {
                            getStateBuilder(state).getPatternBuilder()
                                                  .append('|');
                        }
                        compileTerminal(state, syms.get(0).getContent(),
                                        nextState);
                        break;
                }
            }
            return st;
        }

        protected State freeze(State base, Map<String, State> memo,
                               Map<String, List<State>> successorMemo) {
            if (base == null) return null;
            State ret = memo.get(base.getName());
            if (ret == null) {
                List<State> successors = new ArrayList<State>();
                ret = new FinalState(base.getName(), base.getPattern(),
                    Collections.unmodifiableList(new ArrayList<String>(
                        base.getGroupNames())),
                    Collections.unmodifiableList(successors),
                    base.isAccepting());
                memo.put(ret.getName(), ret);
                successorMemo.put(ret.getName(), successors);
                for (State s : base.getSuccessors()) {
                    successors.add(freeze(s, memo, successorMemo));
                }
            }
            return ret;
        }

        public State call() throws InvalidGrammarException {
            return freeze(
                compileState(LexerGrammar.START_SYMBOL.getContent()),
                new HashMap<String, State>(),
                new HashMap<String, List<State>>());
        }

    }

    private static final int BUFFER_SIZE = 8192;

    // I wonder if there is a more elegant way of constructing this.
    private static final Pattern MATCH_NOTHING = Pattern.compile("[0&&1]");

    private final CompiledGrammar grammar;
    private final LineColumnReader input;
    private final StringBuilder inputBuffer;
    private final LineColumnReader.CoordinatesTracker inputPosition;
    private final Matcher matcher;
    private State state;
    private boolean atEOF;
    private Token outputBuffer;

    public Lexer(CompiledGrammar grammar, LineColumnReader input) {
        this.grammar = grammar;
        this.input = input;
        this.inputBuffer = new StringBuilder();
        this.inputPosition = new LineColumnReader.CoordinatesTracker();
        this.state = grammar.getInitialState();
        this.matcher = state.getPattern().matcher(inputBuffer);
        this.atEOF = false;
        this.outputBuffer = null;
        matcher.useAnchoringBounds(false);
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

    protected int pullInput() throws IOException {
        char[] data = new char[BUFFER_SIZE];
        int ret = input.read(data);
        if (ret < 0) return ret;
        inputBuffer.append(data, 0, ret);
        matcher.reset();
        return ret;
    }
    protected Token consumeInput(int length, int groupIdx) {
        String tokenContent = inputBuffer.substring(0, length);
        inputBuffer.delete(0, length);
        matcher.reset();
        Token ret = new Token(copyPosition(),
            state.getGroupNames().get(groupIdx - 1), tokenContent);
        inputPosition.advance(tokenContent, 0, length);
        return ret;
    }
    protected void advance(int groupIdx) {
        State oldState = state;
        state = state.getSuccessors().get(groupIdx - 1);
        if (oldState == state) {
            /* NOP */
        } else if (state == null) {
            matcher.usePattern(MATCH_NOTHING);
        } else {
            matcher.usePattern(state.getPattern());
        }
    }

    public Token peek() throws IOException, LexingException {
        if (outputBuffer != null)
            return outputBuffer;
        for (;;) {
            if (state != null && matcher.lookingAt() &&
                    (atEOF || ! matcher.hitEnd())) {
                int groupIdx = chooseMatchedGroup(matcher);
                outputBuffer = consumeInput(matcher.end(), groupIdx);
                advance(groupIdx);
                return outputBuffer;
            } else if (atEOF) {
                if (state != null && ! state.isAccepting()) {
                    LineColumnReader.Coordinates pos = copyPosition();
                    throw new LexingException(pos,
                        "Unexpected end of input at " + pos);
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
        matcher.reset();
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
