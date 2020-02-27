package net.instant.util.parser;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    public static class Token implements NamedValue {

        private final String name;
        private final LineColumnReader.Coordinates position;
        private final String content;

        public Token(String name, LineColumnReader.Coordinates position,
                     String content) {
            if (position == null)
                throw new NullPointerException(
                    "Token coordinates may not be null");
            if (content == null)
                throw new NullPointerException(
                    "Token content may not be null");
            this.name = name;
            this.position = position;
            this.content = content;
        }

        public String toString() {
            return String.format(
                "%s@%h[name=%s,position=%s,content=%s]",
                getClass().getName(), this, getName(), getPosition(),
                getContent());
        }
        public String toUserString() {
            String name = getName();
            return String.format("%s%s at %s",
                Formats.formatString(getContent()),
                ((name == null) ? "" : " (" + name + ")"),
                getPosition());
        }

        public boolean equals(Object other) {
            if (! (other instanceof Token)) return false;
            Token to = (Token) other;
            return (getPosition().equals(to.getPosition()) &&
                    equalOrNull(getName(), to.getName()) &&
                    getContent().equals(to.getContent()));
        }

        public int hashCode() {
            return hashCodeOrNull(getName()) ^ getPosition().hashCode() ^
                getContent().hashCode();
        }

        public String getName() {
            return name;
        }

        public LineColumnReader.Coordinates getPosition() {
            return position;
        }

        public String getContent() {
            return content;
        }

        public boolean matches(Grammar.Symbol sym) {
            if (sym.getType() == Grammar.SymbolType.NONTERMINAL) {
                return sym.getContent().equals(getName());
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

        public static TokenPattern create(String name,
                                          Set<Grammar.Production> prods)
                throws InvalidGrammarException {
            if (prods.size() == 0)
                throw new InvalidGrammarException(
                    "Missing definition of token " + name);
            if (prods.size() > 1)
                throw new InvalidGrammarException(
                    "Multiple productions for token " + name);
            Grammar.Production pr = prods.iterator().next();
            if (pr.getSymbols().size() != 1)
                throw new InvalidGrammarException("Token " +
                    name + " definition must contain exactly one " +
                    "nonterminal");
            Grammar.Symbol sym = pr.getSymbols().get(0);
            if (sym.getType() == Grammar.SymbolType.NONTERMINAL)
                throw new InvalidGrammarException("Token " + name +
                    " definition may not contain nonterminals");
            return new TokenPattern(name, sym.getType(), sym.getPattern());
        }

    }

    protected interface State extends NamedValue {

        Map<String, TokenPattern> getPatterns();

        Map<String, State> getSuccessors();

        boolean isAccepting();

    }

    protected static class StandardState implements State {

        private final String name;
        private final Map<String, TokenPattern> patterns;
        private final Map<String, State> successors;
        private boolean accepting;

        public StandardState(String name) {
            this.name = name;
            this.patterns = new NamedMap<TokenPattern>();
            this.successors = new LinkedHashMap<String, State>();
            this.accepting = false;
        }

        public String getName() {
            return name;
        }

        public Map<String, TokenPattern> getPatterns() {
            return patterns;
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
        private final Map<String, TokenPattern> tokens;
        private final Map<String, StandardState> states;
        private final Set<String> seenStates;

        public Compiler(LexerGrammar grammar) throws InvalidGrammarException {
            this.grammar = new LexerGrammar(grammar);
            this.tokens = new NamedMap<TokenPattern>();
            this.states = new NamedMap<StandardState>();
            this.seenStates = new HashSet<String>();
            this.grammar.validate();
        }

        protected LexerGrammar getGrammar() {
            return grammar;
        }

        protected StandardState getState(String name) {
            if (name == null) return null;
            StandardState ret = states.get(name);
            if (ret == null) {
                ret = new StandardState(name);
                states.put(name, ret);
            }
            return ret;
        }

        protected TokenPattern compileToken(String name)
                throws InvalidGrammarException {
            return TokenPattern.create(name, grammar.getProductions(name));
        }

        protected TokenPattern getToken(String name)
                throws InvalidGrammarException {
            TokenPattern ret = tokens.get(name);
            if (ret == null) {
                ret = compileToken(name);
                tokens.put(name, ret);
            }
            return ret;
        }

        protected void compileStateTransition(String name, String token,
                String nextName) throws InvalidGrammarException {
            StandardState st = getState(name);
            if (st.getSuccessors().containsKey(token))
                throw new InvalidGrammarException(
                    "Redundant transitions for state " + name +
                    " with token " + token);
            st.getPatterns().put(token, getToken(token));
            st.getSuccessors().put(token, getState(nextName));
        }

        @SuppressWarnings("fallthrough")
        protected StandardState compileState(String name)
                throws InvalidGrammarException {
            StandardState st = getState(name);
            if (seenStates.contains(name)) return st;
            seenStates.add(name);
            for (Grammar.Production pr : grammar.getProductions(name)) {
                List<Grammar.Symbol> syms = pr.getSymbols();
                String nextName = null;
                switch (syms.size()) {
                    case 0:
                        st.setAccepting(true);
                        break;
                    case 2:
                        // Index-1 symbols are validated to be nonterminals.
                        nextName = syms.get(1).getContent();
                        compileState(nextName);
                    case 1:
                        Grammar.Symbol sym = syms.get(0);
                        if (sym.getType() != Grammar.SymbolType.NONTERMINAL)
                            throw new InvalidGrammarException("Lexer " +
                                "grammar state productions may only " +
                                "contain nonterminals");
                        compileStateTransition(name,
                            syms.get(0).getContent(), nextName);
                        break;
                }
            }
            return st;
        }

        public State call() throws InvalidGrammarException {
            return compileState(LexerGrammar.START_SYMBOL.getContent());
        }

    }

    private enum MatcherListState { READY, NEED_RESET, NEED_REBUILD }

    private static final int BUFFER_SIZE = 8192;

    private final CompiledGrammar grammar;
    private final LineColumnReader input;
    private final StringBuilder inputBuffer;
    private final LineColumnReader.CoordinatesTracker inputPosition;
    private final Map<String, Matcher> matchers;
    private State state;
    private boolean atEOF;
    private Token outputBuffer;
    private MatcherListState matchersState;

    public Lexer(CompiledGrammar grammar, LineColumnReader input) {
        this.grammar = grammar;
        this.input = input;
        this.inputBuffer = new StringBuilder();
        this.inputPosition = new LineColumnReader.CoordinatesTracker();
        this.matchers = new LinkedHashMap<String, Matcher>();
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

    protected Map<String, Matcher> getMatchers() {
        Map<String, TokenPattern> patterns = getState().getPatterns();
        StringBuilder buf = getInputBuffer();
        switch (matchersState) {
            case NEED_REBUILD:
                for (Map.Entry<String, TokenPattern> ent :
                     patterns.entrySet()) {
                    Matcher m = matchers.get(ent.getKey());
                    if (m != null) {
                        m.reset(buf);
                    } else {
                        m = ent.getValue().getPattern().matcher(buf);
                        m.useAnchoringBounds(false);
                        matchers.put(ent.getKey(), m);
                    }
                }
                break;
            case NEED_RESET:
                for (String name : patterns.keySet()) {
                    matchers.get(name).reset(buf);
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
        setOutputBuffer(null);
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
    protected Token createToken(int length, String name) {
        return new Token(name, getInputPosition(),
                         getInputBuffer().substring(0, length));
    }
    protected void advance(Token tok) {
        String content = tok.getContent();
        getInputBuffer().delete(0, content.length());
        getRawPosition().advance(content, 0, content.length());
        setState(getState().getSuccessors().get(tok.getName()));
        setMatchersState(MatcherListState.NEED_RESET);
    }

    protected Token doMatch() throws LexingException {
        if (getState() == null) return null;
        Map<String, TokenPattern> patterns = getState().getPatterns();
        Map<String, Matcher> matchers = getMatchers();
        String bestName = null;
        int bestSize = Integer.MIN_VALUE;
        int bestRank = Integer.MAX_VALUE;
        for (String thisName : patterns.keySet()) {
            Matcher m = matchers.get(thisName);
            boolean matched = m.lookingAt();
            if (m.hitEnd() && ! isAtEOF()) return null;
            if (! matched) continue;
            int thisSize = m.end();
            int thisRank = patterns.get(thisName).getType().ordinal();
            if (thisSize < bestSize ||
                    (thisSize == bestSize && thisRank > bestRank)) {
                continue;
            } else if (thisSize == bestSize && thisRank == bestRank) {
                throw new LexingException(getInputPosition(), "Ambiguous " +
                    "classifications for prospective token " +
                    Formats.formatString(m.group()) + " at " +
                    getInputPosition() + ": " +
                    bestName + " and " + thisName);
            }
            bestName = thisName;
            bestSize = thisSize;
            bestRank = thisRank;
        }
        if (bestName == null) return null;
        return createToken(matchers.get(bestName).end(), bestName);
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
        if (ret != null) advance(ret);
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
