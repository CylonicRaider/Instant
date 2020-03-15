package net.instant.util.parser;

import java.util.Set;

public interface GrammarView {

    Set<String> getProductionNames();

    Set<Production> getProductions(String name);

}
