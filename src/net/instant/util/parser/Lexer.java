package net.instant.util.parser;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.instant.util.LineColumnReader;

public class Lexer implements Closeable {

    public static class LexerGrammar extends Grammar {

        public static final String START_SYMBOL = "$tokens";

        public LexerGrammar() {
            super();
        }
        public LexerGrammar(GrammarView copyFrom) {
            super(copyFrom);
        }
        public LexerGrammar(Production... productions) {
            super(productions);
        }

        private void validateAcyclicity(String name, Set<String> stack,
                Set<String> seen) throws InvalidGrammarException {
            if (seen.contains(name))
                return;
            if (stack.contains(name))
                throw new InvalidGrammarException(
                    "LexerGrammar contains production cycle");
            stack.add(name);
            for (Production pr : getRawProductions(name)) {
                for (Symbol sym : pr.getSymbols()) {
                    if (sym.getType() != SymbolType.NONTERMINAL)
                        continue;
                    validateAcyclicity(sym.getContent(), stack, seen);
                }
            }
            stack.remove(name);
            seen.add(name);
        }
        protected void validate(String startSymbol)
                throws InvalidGrammarException {
            super.validate(startSymbol);
            for (Production pr : getRawProductions(startSymbol)) {
                List<Symbol> syms = pr.getSymbols();
                if (syms.size() != 1)
                    throw new InvalidGrammarException("LexerGrammar start " +
                        "symbol productions must have exactly one symbol each");
                if (syms.get(0).getType() != SymbolType.NONTERMINAL)
                    throw new InvalidGrammarException("LexerGrammar start " +
                        "symbol production symbols must be nonterminals");
            }
            validateAcyclicity(startSymbol, new HashSet<String>(),
                               new HashSet<String>());
        }
        public void validate() throws InvalidGrammarException {
            validate(START_SYMBOL);
        }

    }

    public static class CompiledGrammar {

        private final Pattern pattern;
        private final List<String> prodNames;

        public CompiledGrammar(Pattern pattern, List<String> prodNames) {
            this.pattern = pattern;
            this.prodNames = prodNames;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public List<String> getProductionNames() {
            return prodNames;
        }

        public Lexer makeLexer(LineColumnReader input) {
            return new Lexer(this, input);
        }
        public Lexer makeLexer(Reader input) {
            return new Lexer(this, input);
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

        private static boolean equalOrNull(String a, String b) {
            return (a == null) ? (b == null) : a.equals(b);
        }
        private static int hashCodeOrNull(Object o) {
            return (o == null) ? 0 : o.hashCode();
        }

    }

    public class LexerException extends BaseParserException {

        private final LineColumnReader.Coordinates position;

        public LexerException(LineColumnReader.Coordinates pos) {
            super();
            position = pos;
        }
        public LexerException(LineColumnReader.Coordinates pos,
                              String message) {
            super(message);
            position = pos;
        }
        public LexerException(LineColumnReader.Coordinates pos,
                              Throwable cause) {
            super(cause);
            position = pos;
        }
        public LexerException(LineColumnReader.Coordinates pos,
                              String message, Throwable cause) {
            super(message, cause);
            position = pos;
        }

        public LineColumnReader.Coordinates getPosition() {
            return position;
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
    public Lexer(CompiledGrammar grammar, Reader input) {
        this(grammar, new LineColumnReader(input));
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
            new LineColumnReader.CoordinatesTracker(inputPosition),
            production, tokenText);
        inputPosition.advance(tokenText, 0, length);
        return ret;
    }

    public Token peek() throws IOException, LexerException {
        if (outputBuffer != null)
            return outputBuffer;
        for (;;) {
            if (matcher.lookingAt() && (atEOF || ! matcher.hitEnd())) {
                int groupIdx = CompiledGrammar.findMatchedGroup(matcher);
                outputBuffer = consumeInput(matcher.end(),
                    grammar.getProductionNames().get(groupIdx));
                return outputBuffer;
            } else if (atEOF) {
                if (inputBuffer.length() == 0)
                    return null;
                throw new LexerException(
                    new LineColumnReader.FixedCoordinates(inputPosition),
                    "Unconsumed input");
            }
            if (pullInput() == -1) atEOF = true;
        }
    }

    public Token read() throws IOException, LexerException {
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

    private static void compileProductions(LexerGrammar g,
            String name, boolean top, StringBuilder sb, List<String> names) {
        boolean first = true;
        for (Grammar.Production pr : g.getProductions(name)) {
            if (top && pr.getSymbols().size() != 1)
                throw new IllegalArgumentException(
                    "Trying to compile a grammar with incorrect " +
                    LexerGrammar.START_SYMBOL +
                    " production symbol counts?!");
            if (first) {
                first = false;
            } else {
                sb.append('|');
            }
            for (Grammar.Symbol sym : pr.getSymbols()) {
                if (top) {
                    if (sym.getType() != Grammar.SymbolType.NONTERMINAL)
                        throw new IllegalArgumentException(
                            "Trying to compile a grammar with invalid " +
                            LexerGrammar.START_SYMBOL +
                            " production symbols?!");
                    names.add(pr.getName());
                    sb.append('(');
                } else {
                    sb.append("(?:");
                }
                if (sym.getType() == Grammar.SymbolType.NONTERMINAL) {
                    compileProductions(g, sym.getContent(), false, sb, null);
                } else {
                    sb.append(sym.getPattern().pattern());
                }
                sb.append(')');
            }
        }
    }
    public static CompiledGrammar compile(LexerGrammar g)
            throws InvalidGrammarException {
        LexerGrammar lg = new LexerGrammar(g);
        lg.validate();
        StringBuilder sb = new StringBuilder();
        ArrayList<String> prodNames = new ArrayList<String>();
        compileProductions(lg, LexerGrammar.START_SYMBOL, true, sb,
                           prodNames);
        prodNames.trimToSize();
        return new CompiledGrammar(Pattern.compile(sb.toString()),
                                   Collections.unmodifiableList(prodNames));
    }

}
