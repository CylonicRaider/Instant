package net.instant.util.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Grammar {

    public enum SymbolType { TERMINAL, NONTERMINAL }

    public static class Symbol {

        private final SymbolType type;
        private final String content;

        public Symbol(SymbolType type, String content) {
            if (type == null)
                throw new NullPointerException("Symbol type may not be null");
            if (content == null)
                throw new NullPointerException(
                    "Symbol content may not be null");
            this.type = type;
            this.content = content;
        }

        public boolean equals(Object other) {
            if (! (other instanceof Symbol)) return false;
            Symbol so = (Symbol) other;
            return (getType() == so.getType() &&
                    getContent().equals(so.getContent()));
        }

        public int hashCode() {
            return getType().hashCode() ^ getContent().hashCode();
        }

        public SymbolType getType() {
            return type;
        }

        public String getContent() {
            return content;
        }

    }

    public static class Production {

        private final String name;
        private final List<Symbol> symbols;

        public Production(String name, List<Symbol> symbols) {
            if (name == null)
                throw new NullPointerException(
                    "Production name may not be null");
            if (symbols == null)
                throw new NullPointerException(
                    "Production symbols may not be null");
            this.name = name;
            this.symbols = Collections.unmodifiableList(
                new ArrayList<Symbol>(symbols));
        }

        public boolean equals(Object other) {
            if (! (other instanceof Production)) return false;
            Production po = (Production) other;
            return (getName().equals(po.getName()) &&
                    getSymbols().equals(po.getSymbols()));
        }

        public int hashCode() {
            return getName().hashCode() ^ getSymbols().hashCode();
        }

        public String getName() {
            return name;
        }

        public List<Symbol> getSymbols() {
            return symbols;
        }

    }

    private final Map<String, Set<Production>> productions;
    private final Map<String, Set<Production>> productionsView;

    public Grammar() {
        productions = new LinkedHashMap<String, Set<Production>>();
        productionsView = Collections.unmodifiableMap(productions);
    }

    public Map<String, Set<Production>> getProductions() {
        return productionsView;
    }

    private Set<Production> getProductionSet(String name, boolean create) {
        Set<Production> ret = productions.get(name);
        if (ret == null && create) {
            ret = new LinkedHashSet<Production>();
            productions.put(name, ret);
        }
        return ret;
    }
    public void addProduction(Production prod) {
        getProductionSet(prod.getName(), true).add(prod);
    }
    public void removeProduction(Production prod) {
        Set<Production> subset = getProductionSet(prod.getName(), false);
        if (subset == null) return;
        subset.remove(prod);
        if (subset.isEmpty()) productions.remove(prod.getName());
    }

}
