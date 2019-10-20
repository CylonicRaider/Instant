package net.instant.util.parser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LexerGrammar extends Grammar {

    public static final String LEXER_START_SYMBOL = "$tokens";

    public LexerGrammar() {
        super();
    }
    public LexerGrammar(Grammar copyFrom) {
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
        for (Production pr : getProductions().get(name)) {
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
        for (Production pr : getProductions().get(startSymbol)) {
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
        validate(LEXER_START_SYMBOL);
    }

}
