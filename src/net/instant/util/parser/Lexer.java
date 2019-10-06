package net.instant.util.parser;

import java.util.regex.Pattern;

public class Lexer {

    public static class CompiledGrammar {

        private final Pattern pattern;

        public CompiledGrammar(Pattern pattern) {
            this.pattern = pattern;
        }

        public Pattern getPattern() {
            return pattern;
        }

    }

    private static void compileProductions(LexicalGrammar g,
            String name, StringBuilder sb) {
        boolean first = true;
        for (Grammar.Production pr : g.getProductions(name)) {
            if (first) {
                first = false;
            } else {
                sb.append('|');
            }
            for (Grammar.Symbol sym : pr.getSymbols()) {
                sb.append("(?:");
                if (sym.getType() == Grammar.SymbolType.NONTERMINAL) {
                    compileProductions(g, sym.getContent(), sb);
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
        compileProductions(cg, LexicalGrammar.LEXER_START_SYMBOL, sb);
        return new CompiledGrammar(Pattern.compile(sb.toString()));
    }

}
