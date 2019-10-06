package net.instant.util.parser;

import java.util.HashSet;
import java.util.Set;

public class LexicalGrammar extends Grammar {

    public static final String LEXER_START_SYMBOL = "$tokens";

    public LexicalGrammar() {
        super();
    }
    public LexicalGrammar(Grammar copyFrom) {
        super(copyFrom);
    }

    private void validateAcyclicity(String name, Set<String> stack,
            Set<String> seen) throws InvalidGrammarException {
        if (seen.contains(name))
            return;
        if (stack.contains(name))
            throw new InvalidGrammarException(
                "LexicalGrammar contains production cycle");
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
    public void validate() throws InvalidGrammarException {
        super.validate();
        validateAcyclicity(LEXER_START_SYMBOL, new HashSet<String>(),
                           new HashSet<String>());
    }

}
