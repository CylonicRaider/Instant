package net.instant.util.parser;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import net.instant.util.Formats;
import net.instant.util.LineColumnReader;
import net.instant.util.NamedMap;

public class Lexer implements Closeable {

    public enum MatchStatus { OK, NO_MATCH, EOI }

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

    protected interface State {

        Map<String, TokenPattern> getPatterns();

        boolean isCompatibleWith(State other);

        boolean contains(Token tok);

    }

    protected static class StandardState implements State {

        private final Map<String, TokenPattern> patterns;
        private final Map<State, Boolean> compatibles;

        public StandardState(Map<String, TokenPattern> patterns) {
            this.patterns = new NamedMap<TokenPattern>(
                new LinkedHashMap<String, TokenPattern>(patterns));
            this.compatibles = new HashMap<State, Boolean>();
        }
        public StandardState() {
            this(Collections.<String, TokenPattern>emptyMap());
        }

        public Map<String, TokenPattern> getPatterns() {
            return patterns;
        }

        public boolean isCompatibleWith(State other) {
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
    private final LineColumnReader.CoordinatesTracker inputPosition;
    private final Map<String, Matcher> matchers;
    private State state;
    private boolean atEOI;
    private MatchStatus matchStatus;
    private Token token;
    private MatcherListState matchersState;

    public Lexer(LineColumnReader input) {
        this.input = input;
        this.inputBuffer = new StringBuilder();
        this.inputPosition = new LineColumnReader.CoordinatesTracker();
        this.matchers = new LinkedHashMap<String, Matcher>();
        this.state = null;
        this.atEOI = false;
        this.matchStatus = null;
        this.token = null;
        this.matchersState = MatcherListState.NEED_REBUILD;
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

    protected State getState() {
        return state;
    }
    protected void setState(State s) {
        if (s == state) return;
        State os = state;
        state = s;
        MatchStatus ms = getMatchStatus();
        Token tok = getToken();
        if (ms != null && (s == null || os == null ||
                           (ms == MatchStatus.OK && ! s.contains(tok)) ||
                           ! s.isCompatibleWith(os))) {
            setMatchStatus(null);
            setToken(null);
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

    public Token getToken() {
        return token;
    }
    public void setToken(Token t) {
        token = t;
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
            setAtEOI(true);
            return ret;
        }
        getInputBuffer().append(data, 0, ret);
        setMatchersState(MatcherListState.NEED_RESET);
        return ret;
    }
    protected Token createToken(int length, String name) {
        return getState().getPatterns().get(name).createToken(
            getInputPosition(), getInputBuffer().substring(0, length));
    }
    protected void advance(Token tok) {
        String content = tok.getContent();
        getInputBuffer().delete(0, content.length());
        getRawPosition().advance(content, 0, content.length());
        setMatchersState(MatcherListState.NEED_RESET);
    }

    protected MatchStatus doMatchBuffer() throws LexingException {
        if (getState() == null) return MatchStatus.NO_MATCH;
        Map<String, TokenPattern> patterns = getState().getPatterns();
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
        if (bestName == null) return MatchStatus.NO_MATCH;
        setToken(createToken(matchers.get(bestName).end(), bestName));
        return MatchStatus.OK;
    }
    protected MatchStatus doMatch() throws LexingException {
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

    protected LexingException unexpectedInput() {
        LineColumnReader.Coordinates pos = getInputPosition();
        // If there is any unexpected input, we can as well blame its first
        // character (perhaps it is the *reason* the input is unexpected)?
        String message = (getInputBuffer().length() == 0) ?
            "Unexpected end of input" :
            "Unexpected character " + Formats.formatCharacter(
                Character.codePointAt(getInputBuffer(), 0));
        return new LexingException(pos, message + " at " + pos);
    }

    @SuppressWarnings("fallthrough")
    public MatchStatus peek() throws LexingException {
        MatchStatus st = getMatchStatus();
        if (st != null) return st;
        st = doMatch();
        setMatchStatus(st);
        switch (st) {
            case EOI:
                setState(null);
            case NO_MATCH:
                setToken(null);
                break;
        }
        return st;
    }

    public Token next() throws LexingException {
        MatchStatus st = peek();
        Token tok = getToken();
        switch (st) {
            case OK:
                advance(tok);
                break;
            case NO_MATCH:
                throw unexpectedInput();
            case EOI:
                throw new LexingException(getInputPosition(),
                                          "No more input to advance past");
        }
        setMatchStatus(null);
        setToken(null);
        return tok;
    }

    public void close() throws IOException {
        input.close();
        inputBuffer.setLength(0);
        matchers.clear();
        state = null;
        atEOI = true;
        matchStatus = null;
        token = null;
        matchersState = null;
    }

}
