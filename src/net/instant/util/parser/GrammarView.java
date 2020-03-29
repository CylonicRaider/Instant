package net.instant.util.parser;

import java.util.Set;

public interface GrammarView {

    Grammar.Nonterminal getStartSymbol();

    Set<String> getProductionNames();

    Set<Grammar.Production> getProductions(String name);

}
