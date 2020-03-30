package net.instant.util.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import net.instant.api.NamedValue;
import net.instant.api.parser.TextLocation;
import net.instant.util.Formats;
import net.instant.util.LineColumnReader;
import net.instant.util.NamedMap;

public class Lexer implements TokenSource {

    public static class Token implements NamedValue {

        private final String name;
        private final TextLocation position;
        private final String content;

        public Token(String name, TextLocation position,
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

        public TextLocation getPosition() {
            return position;
        }

        public String getContent() {
            return content;
        }

        public boolean matches(Grammar.Symbol sym) {
            if (sym instanceof Grammar.Nonterminal) {
                return ((Grammar.Nonterminal) sym).getReference()
                    .equals(getName());
            } else if (sym instanceof Grammar.Terminal) {
                return ((Grammar.Terminal) sym).getPattern()
                    .matcher(getContent()).matches();
            } else {
                throw new IllegalArgumentException("Unrecognized symbol " +
                    sym);
            }
        }
        public boolean matches(TokenPattern pat) {
            return (equalOrNull(getName(), pat.getName()) &&
                    pat.matcher(getContent()).matches());
        }

        private static boolean equalOrNull(String a, String b) {
            return (a == null) ? (b == null) : a.equals(b);
        }
        private static int hashCodeOrNull(Object o) {
            return (o == null) ? 0 : o.hashCode();
        }

    }

    public static class TokenPattern implements NamedValue {

        private final String name;
        private final Grammar.Terminal symbol;

        public TokenPattern(String name, Grammar.Terminal symbol) {
            if (name == null)
                throw new NullPointerException(
                    "TokenPattern name may not be null");
            if (symbol == null)
                throw new NullPointerException(
                    "TokenPattern symbol may not be null");
            this.name = name;
            this.symbol = symbol;
        }

        public String toString() {
            return String.format("%s@%h[name=%s,symbol=%s]",
                getClass().getName(), this, getName(), getSymbol());
        }

        public boolean equals(Object other) {
            if (! (other instanceof TokenPattern)) return false;
            TokenPattern to = (TokenPattern) other;
            return (getName().equals(to.getName()) &&
                    getSymbol().equals(to.getSymbol()));
        }

        public int hashCode() {
            return getName().hashCode() ^ getSymbol().hashCode();
        }

        public String getName() {
            return name;
        }

        public Grammar.Terminal getSymbol() {
            return symbol;
        }

        public Matcher matcher(CharSequence input) {
            return getSymbol().getPattern().matcher(input);
        }

        public Token createToken(TextLocation position, String content) {
            return new Token(getName(), position, content);
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
                    "nonterminal, got " + pr.getSymbols().size() +
                    " instead");
            Grammar.Symbol sym = pr.getSymbols().get(0);
            if (! (sym instanceof Grammar.Terminal))
                throw new InvalidGrammarException("Token " + name +
                    " definition may only contain terminals, got " + sym +
                    " instead");
            return new TokenPattern(name, (Grammar.Terminal) sym);
        }

    }

    protected static class StandardState implements Selection {

        private final Map<String, TokenPattern> patterns;
        private final Map<Selection, Boolean> compatibles;

        public StandardState(Map<String, TokenPattern> patterns) {
            this.patterns = new NamedMap<TokenPattern>(
                new LinkedHashMap<String, TokenPattern>(patterns));
            this.compatibles = new HashMap<Selection, Boolean>();
        }
        public StandardState() {
            this(Collections.<String, TokenPattern>emptyMap());
        }

        public Map<String, TokenPattern> getPatterns() {
            return patterns;
        }

        public boolean isCompatibleWith(Selection other) {
            Boolean ret = compatibles.get(other);
            if (ret == null) {
                ret = other.getPatterns().keySet().containsAll(
                    getPatterns().keySet());
                compatibles.put(other, ret);
            }
            return ret;
        }

        public boolean contains(Token tok) {
            return getPatterns().containsKey(tok.getName());
        }

    }

    private enum MatcherListState { READY, NEED_RESET, NEED_REBUILD }

    private static final int BUFFER_SIZE = 8192;

    private final LineColumnReader input;
    private final StringBuilder inputBuffer;
    private final LineColumnReader.LocationTracker inputPosition;
    private final Map<String, Matcher> matchers;
    private Selection state;
    private boolean atEOI;
    private MatchStatus matchStatus;
    private Token currentToken;
    private MatcherListState matchersState;

    public Lexer(LineColumnReader input) {
        this.input = input;
        this.inputBuffer = new StringBuilder();
        this.inputPosition = new LineColumnReader.LocationTracker();
        this.matchers = new LinkedHashMap<String, Matcher>();
        this.state = null;
        this.atEOI = false;
        this.matchStatus = null;
        this.currentToken = null;
        this.matchersState = MatcherListState.NEED_REBUILD;
    }

    protected Reader getInput() {
        return input;
    }

    protected StringBuilder getInputBuffer() {
        return inputBuffer;
    }

    public TextLocation getCurrentPosition() {
        return getInputPosition();
    }
    public TextLocation getInputPosition() {
        return new LineColumnReader.FixedLocation(inputPosition);
    }
    protected LineColumnReader.LocationTracker getRawPosition() {
        return inputPosition;
    }

    protected Map<String, Matcher> getMatchers() {
        Map<String, TokenPattern> patterns = getSelection().getPatterns();
        StringBuilder buf = getInputBuffer();
        switch (matchersState) {
            case NEED_REBUILD:
                for (Map.Entry<String, TokenPattern> ent :
                     patterns.entrySet()) {
                    Matcher m = matchers.get(ent.getKey());
                    if (m != null) {
                        m.reset(buf);
                    } else {
                        m = ent.getValue().getSymbol().getPattern()
                            .matcher(buf);
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

    protected Selection getSelection() {
        return state;
    }
    public void setSelection(Selection s) {
        if (s == state) return;
        Selection os = state;
        state = s;
        MatchStatus ms = getMatchStatus();
        Token tok = getCurrentToken();
        if (ms != null && (s == null || os == null ||
                           (ms == MatchStatus.OK && ! s.contains(tok)) ||
                           ! s.isCompatibleWith(os))) {
            setMatchStatus(null);
            setCurrentToken(null);
        }
        setMatchersState(MatcherListState.NEED_REBUILD);
    }

    protected boolean isAtEOI() {
        return atEOI;
    }
    protected void setAtEOI(boolean v) {
        atEOI = v;
    }

    protected MatchStatus getMatchStatus() {
        return matchStatus;
    }
    protected void setMatchStatus(MatchStatus st) {
        matchStatus = st;
    }

    public Token getCurrentToken() {
        return currentToken;
    }
    public void setCurrentToken(Token t) {
        currentToken = t;
    }

    private void setMatchersState(MatcherListState st) {
        if (st.ordinal() > matchersState.ordinal())
            matchersState = st;
    }

    protected int pullInput() throws MatchingException {
        return pullInput(BUFFER_SIZE);
    }
    protected int pullInput(int size) throws MatchingException {
        char[] data = new char[size];
        int ret;
        try {
            ret = getInput().read(data);
        } catch (IOException exc) {
            throw new MatchingException(getInputPosition(), exc);
        }
        if (ret < 0) {
            setAtEOI(true);
            return ret;
        }
        getInputBuffer().append(data, 0, ret);
        setMatchersState(MatcherListState.NEED_RESET);
        return ret;
    }
    protected Token createToken(int length, String name) {
        return getSelection().getPatterns().get(name).createToken(
            getInputPosition(), getInputBuffer().substring(0, length));
    }
    protected void advance(Token tok) {
        String content = tok.getContent();
        getInputBuffer().delete(0, content.length());
        getRawPosition().advance(content, 0, content.length());
        setMatchersState(MatcherListState.NEED_RESET);
    }

    protected MatchStatus doMatchBuffer() throws MatchingException {
        if (getSelection() == null) return MatchStatus.NO_MATCH;
        Map<String, TokenPattern> patterns = getSelection().getPatterns();
        Map<String, Matcher> matchers = getMatchers();
        String bestName = null;
        int bestSize = Integer.MIN_VALUE;
        int bestRank = Integer.MAX_VALUE;
        for (String thisName : patterns.keySet()) {
            Matcher m = matchers.get(thisName);
            boolean matched = m.lookingAt();
            if (m.hitEnd() && ! isAtEOI()) return MatchStatus.EOI;
            if (! matched) continue;
            int thisSize = m.end();
            int thisRank = patterns.get(thisName).getSymbol().getMatchRank();
            if (thisSize < bestSize ||
                    (thisSize == bestSize && thisRank < bestRank)) {
                continue;
            } else if (thisSize == bestSize && thisRank == bestRank) {
                throw new MatchingException(getInputPosition(), "Ambiguous " +
                    "classifications for prospective token " +
                    Formats.formatString(m.group()) + " at " +
                    getInputPosition() + ": " +
                    bestName + " and " + thisName);
            }
            bestName = thisName;
            bestSize = thisSize;
            bestRank = thisRank;
        }
        if (bestName == null) return MatchStatus.NO_MATCH;
        setCurrentToken(createToken(matchers.get(bestName).end(), bestName));
        return MatchStatus.OK;
    }
    protected MatchStatus doMatch() throws MatchingException {
        for (;;) {
            switch (doMatchBuffer()) {
                case OK:
                    return MatchStatus.OK;
                case NO_MATCH:
                    if (isAtEOI() && getInputBuffer().length() == 0)
                        return MatchStatus.EOI;
                    return MatchStatus.NO_MATCH;
                case EOI:
                    pullInput();
                    break;
            }

        }
    }

    protected MatchingException unexpectedInput() {
        TextLocation pos = getInputPosition();
        // If there is any unexpected input, we can as well blame its first
        // character (perhaps it is the *reason* the input is unexpected)?
        String message = (getInputBuffer().length() == 0) ?
            "Unexpected end of input" :
            "Unexpected character " + Formats.formatCharacter(
                Character.codePointAt(getInputBuffer(), 0));
        return new MatchingException(pos, message + " at " + pos);
    }

    @SuppressWarnings("fallthrough")
    protected MatchStatus peek() throws MatchingException {
        MatchStatus st = getMatchStatus();
        if (st != null) return st;
        st = doMatch();
        setMatchStatus(st);
        switch (st) {
            case EOI:
                setSelection(null);
            case NO_MATCH:
                setCurrentToken(null);
                break;
        }
        return st;
    }
    public MatchStatus peek(boolean required) throws MatchingException {
        MatchStatus ret = peek();
        if (required && ret == MatchStatus.NO_MATCH)
            throw unexpectedInput();
        return ret;
    }

    public Token next() throws MatchingException {
        MatchStatus st = peek();
        Token tok = getCurrentToken();
        switch (st) {
            case OK:
                advance(tok);
                break;
            case NO_MATCH:
                throw unexpectedInput();
            case EOI:
                throw new MatchingException(getInputPosition(),
                                            "No more input to advance past");
        }
        setMatchStatus(null);
        setCurrentToken(null);
        return tok;
    }

    public void close() throws IOException {
        input.close();
        inputBuffer.setLength(0);
        matchers.clear();
        state = null;
        atEOI = true;
        matchStatus = null;
        currentToken = null;
        matchersState = null;
    }

}
