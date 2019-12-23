package net.instant.util.parser;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        private final Pattern pattern;
        private final List<String> groupNames;

        protected CompiledGrammar(LexerGrammar source, Pattern pattern,
                                  List<String> groupNames) {
            this.source = source;
            this.pattern = pattern;
            this.groupNames = groupNames;
        }

        protected GrammarView getSource() {
            return source;
        }

        protected Pattern getPattern() {
            return pattern;
        }

        protected List<String> getGroupNames() {
            return groupNames;
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

        public static int findMatchedGroup(MatchResult res) {
            for (int i = 1; i < res.groupCount(); i++) {
                if (res.start(i) != -1) return i;
            }
            return -1;
        }

    }

    public static class Token {

        private final LineColumnReader.Coordinates position;
        private final String production;
        private final String text;

        public Token(LineColumnReader.Coordinates position,
                     String production, String text) {
            if (position == null)
                throw new NullPointerException(
                    "Token coordinates may not be null");
            if (text == null)
                throw new NullPointerException(
                    "Token text may not be null");
            this.position = position;
            this.production = production;
            this.text = text;
        }

        public String toString() {
            return String.format("%s@%h[position=%s,production=%s,text=%s]",
                getClass().getName(), this, getPosition(), getProduction(),
                getText());
        }
        public String toUserString() {
            String prod = getProduction();
            return String.format("%s%s at %s",
                Formats.formatString(getText()),
                ((prod == null) ? "" : " (" + prod + ")"),
                getPosition());
        }

        public boolean equals(Object other) {
            if (! (other instanceof Token)) return false;
            Token to = (Token) other;
            return (getPosition().equals(to.getPosition()) &&
                    equalOrNull(getProduction(), to.getProduction()) &&
                    getText().equals(to.getText()));
        }

        public int hashCode() {
            return getPosition().hashCode() ^
                hashCodeOrNull(getProduction()) ^ getText().hashCode();
        }

        public LineColumnReader.Coordinates getPosition() {
            return position;
        }

        public String getProduction() {
            return production;
        }

        public String getText() {
            return text;
        }

        public boolean matches(Grammar.Symbol sym) {
            return sym.matches(getText());
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

    protected static class StateBuilder implements NamedValue {

        private final String name;
        private final StringBuilder pattern;
        private final List<String> groupNames;
        private final List<String> nextStates;
        private boolean accepting;

        public StateBuilder(String name) {
            this.name = name;
            this.pattern = new StringBuilder();
            this.groupNames = new ArrayList<String>();
            this.nextStates = new ArrayList<String>();
            this.accepting = false;
        }

        public String getName() {
            return name;
        }

        public StringBuilder getPattern() {
            return pattern;
        }

        public List<String> getGroupNames() {
            return groupNames;
        }

        public List<String> getNextStates() {
            return nextStates;
        }

        public boolean isAccepting() {
            return accepting;
        }
        public void setAccepting(boolean a) {
            accepting = a;
        }

    }

    protected static class Compiler {

        private final LexerGrammar grammar;
        private final Map<String, StateBuilder> states;
        private final Set<String> seenStates;

        public Compiler(LexerGrammar grammar) throws InvalidGrammarException {
            this.grammar = new LexerGrammar(grammar);
            this.states = new NamedMap<StateBuilder>();
            this.seenStates = new HashSet<String>();
            this.grammar.validate();
        }

        protected StateBuilder getStateBuilder(String name) {
            StateBuilder ret = states.get(name);
            if (ret == null) {
                ret = new StateBuilder(name);
                states.put(name, ret);
            }
            return ret;
        }

        protected void compileTerminal(String state, String name,
                String nextState) throws InvalidGrammarException {
            StateBuilder st = getStateBuilder(state);
            StringBuilder pattern = st.getPattern();
            pattern.append('(');
            st.getGroupNames().add(name);
            st.getNextStates().add(nextState);
            boolean first = true;
            for (Grammar.Production pr : grammar.getProductions(name)) {
                if (first) {
                    first = false;
                } else {
                    pattern.append('|');
                }
                if (pr.getSymbols().size() != 1)
                    throw new InvalidGrammarException("Lexer token " + name +
                        " definition must contain exactly one nonterminal " +
                        "each");
                Grammar.Symbol sym = pr.getSymbols().get(0);
                if (sym.getType() == Grammar.SymbolType.NONTERMINAL)
                    throw new InvalidGrammarException("Lexer token " + name +
                        " definition may not contain nonterminals");
                pattern.append("(?:").append(sym.getPattern().pattern())
                       .append(')');
            }
            pattern.append(')');
        }

        @SuppressWarnings("fallthrough")
        protected void compileState(String state)
                throws InvalidGrammarException {
            if (seenStates.contains(state)) return;
            seenStates.add(state);
            StateBuilder st = getStateBuilder(state);
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
                        compileTerminal(state, syms.get(0).getContent(),
                                        nextState);
                        break;
                }
            }
        }

    }

    private static final int BUFFER_SIZE = 8192;

    private final CompiledGrammar grammar;
    private final LineColumnReader input;
    private final StringBuilder inputBuffer;
    private final LineColumnReader.CoordinatesTracker inputPosition;
    private final Matcher matcher;
    private boolean atEOF;
    private Token outputBuffer;

    public Lexer(CompiledGrammar grammar, LineColumnReader input) {
        this.grammar = grammar;
        this.input = input;
        this.inputBuffer = new StringBuilder();
        this.inputPosition = new LineColumnReader.CoordinatesTracker();
        this.matcher = grammar.getPattern().matcher(inputBuffer);
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

    protected int pullInput() throws IOException {
        char[] data = new char[BUFFER_SIZE];
        int ret = input.read(data);
        if (ret < 0) return ret;
        inputBuffer.append(data, 0, ret);
        return ret;
    }
    protected Token consumeInput(int length, String production) {
        String tokenText = inputBuffer.substring(0, length);
        inputBuffer.delete(0, length);
        Token ret = new Token(
            new LineColumnReader.FixedCoordinates(inputPosition),
            production, tokenText);
        inputPosition.advance(tokenText, 0, length);
        return ret;
    }

    public Token peek() throws IOException, LexingException {
        if (outputBuffer != null)
            return outputBuffer;
        for (;;) {
            if (matcher.lookingAt() && (atEOF || ! matcher.hitEnd())) {
                int groupIdx = CompiledGrammar.findMatchedGroup(matcher);
                outputBuffer = consumeInput(matcher.end(),
                    grammar.getGroupNames().get(groupIdx));
                return outputBuffer;
            } else if (atEOF) {
                if (inputBuffer.length() == 0)
                    return null;
                throw new LexingException(
                    new LineColumnReader.FixedCoordinates(inputPosition),
                    "Unconsumed input");
            }
            if (pullInput() == -1) atEOF = true;
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
        throw new AssertionError("NYI");
    }

}
