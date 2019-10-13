package net.instant.util.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import net.instant.util.LineColumnReader;

public class Lexer {

    public static class Token {

        private final LineColumnReader.Coordinates location;
        private final String production;
        private final String text;

        public Token(LineColumnReader.Coordinates location,
                     String production, String text) {
            if (location == null)
                throw new NullPointerException(
                    "Token coordinates may not be null");
            if (text == null)
                throw new NullPointerException(
                    "Token text may not be null");
            this.location = location;
            this.production = production;
            this.text = text;
        }

        public String toString() {
            return String.format("%s@%h[location=%s,production=%s,text=%s]",
                getClass().getName(), this, getLocation(), getProduction(),
                getText());
        }

        public boolean equals(Object other) {
            if (! (other instanceof Token)) return false;
            Token to = (Token) other;
            return (getLocation().equals(to.getLocation()) &&
                    equalOrNull(getProduction(), to.getProduction()) &&
                    getText().equals(to.getText()));
        }

        public int hashCode() {
            return getLocation().hashCode() ^
                hashCodeOrNull(getProduction()) ^ getText().hashCode();
        }

        public LineColumnReader.Coordinates getLocation() {
            return location;
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

    }

    private static void compileProductions(LexicalGrammar g,
            String name, boolean top, StringBuilder sb, List<String> names) {
        boolean first = true;
        for (Grammar.Production pr : g.getProductions(name)) {
            if (top && pr.getSymbols().size() != 1)
                throw new IllegalArgumentException(
                    "Trying to compile a grammar with incorrect " +
                    LexicalGrammar.LEXER_START_SYMBOL +
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
                            LexicalGrammar.LEXER_START_SYMBOL +
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
    public static CompiledGrammar compile(LexicalGrammar g)
            throws InvalidGrammarException {
        LexicalGrammar cg = new LexicalGrammar(g);
        cg.validate();
        StringBuilder sb = new StringBuilder();
        ArrayList<String> prodNames = new ArrayList<String>();
        compileProductions(cg, LexicalGrammar.LEXER_START_SYMBOL, true, sb,
                           prodNames);
        prodNames.trimToSize();
        return new CompiledGrammar(Pattern.compile(sb.toString()),
                                   Collections.unmodifiableList(prodNames));
    }

}
