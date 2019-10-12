package net.instant.util.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class Lexer {

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
