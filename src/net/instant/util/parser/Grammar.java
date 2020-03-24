package net.instant.util.parser;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.instant.util.NamedMap;
import net.instant.util.NamedSet;

public class Grammar implements GrammarView {

    public static final Nonterminal START_SYMBOL =
        new Nonterminal("$start", 0);

    private final NamedMap<NamedSet<Production>> productions;

    public Grammar() {
        productions = new NamedMap<NamedSet<Production>>(
            new LinkedHashMap<String, NamedSet<Production>>());
    }
    public Grammar(GrammarView other) {
        this();
        for (String name : other.getProductionNames()) {
            for (Production prod : other.getProductions(name)) {
                addProduction(prod);
            }
        }
    }
    public Grammar(List<Production> productions) {
        this();
        for (Production p : productions) addProduction(p);
    }
    public Grammar(Production... productions) {
        this(Arrays.asList(productions));
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName());
        sb.append('@');
        sb.append(Integer.toHexString(hashCode()));
        sb.append('[');
        String detail = toStringDetail();
        sb.append(detail);
        boolean first = detail.isEmpty();
        for (Set<Production> ps : productions.values()) {
            for (Production p : ps) {
                if (first) {
                    first = false;
                } else {
                    sb.append(',');
                }
                sb.append(p);
            }
        }
        sb.append(']');
        return sb.toString();
    }
    protected String toStringDetail() {
        return "";
    }

    public Nonterminal createNonterminal(String reference, int flags) {
        return new Nonterminal(reference, flags);
    }
    public Terminal createTerminal(Pattern pattern, int flags) {
        return new Terminal(pattern, flags);
    }
    public FixedTerminal createTerminal(String content, int flags) {
        return new FixedTerminal(content, flags);
    }

    public Production createProduction(String name, List<Symbol> symbols) {
        return new Production(name, symbols);
    }
    public Production createProduction(String name, Symbol... symbols) {
        return new Production(name, symbols);
    }

    // Immutable GrammarView interface.
    public Nonterminal getStartSymbol() {
        return START_SYMBOL;
    }

    public Set<String> getProductionNames() {
        return Collections.unmodifiableSet(productions.keySet());
    }
    public Set<Production> getProductions(String name) {
        return Collections.unmodifiableSet(productions.get(name));
    }

    // Mutable direct interface.
    public boolean isEmpty() {
        return productions.isEmpty();
    }
    public boolean hasProductions(String name) {
        return productions.containsKey(name);
    }
    public NamedMap<NamedSet<Production>> getRawProductions() {
        return productions;
    }
    public NamedSet<Production> getRawProductions(String name) {
        return productions.get(name);
    }
    protected NamedSet<Production> getRawProductions(String name,
                                                     boolean create) {
        NamedSet<Production> ret = productions.get(name);
        if (ret == null && create) {
            ret = new NamedSet<Production>(name,
                                           new LinkedHashSet<Production>());
            productions.put(name, ret);
        }
        return ret;
    }

    public void addProduction(Production prod) {
        getRawProductions(prod.getName(), true).add(prod);
    }
    public void removeProduction(Production prod) {
        Set<Production> subset = getRawProductions(prod.getName(), false);
        if (subset == null) return;
        subset.remove(prod);
        if (subset.isEmpty()) productions.remove(prod.getName());
    }

    protected boolean checkProductions(String name) {
        Set<Production> res = getRawProductions(name, false);
        return (res != null && ! res.isEmpty());
    }
    protected void validate(String startSymbol)
            throws InvalidGrammarException {
        if (! checkProductions(startSymbol))
            throw new InvalidGrammarException("Missing start symbol");
        for (NamedSet<Production> ps : productions.values()) {
            if (! Production.NAME_PATTERN.matcher(ps.getName()).matches())
                throw new InvalidGrammarException("Invalid production name " +
                    ps.getName());
            for (Production p : ps) {
                for (Symbol s : p.getSymbols()) {
                    if (! (s instanceof Nonterminal))
                        continue;
                    if (! checkProductions(((Nonterminal) s).getReference()))
                        throw new InvalidGrammarException("Symbol " + s +
                            " referencing a nonexistent production");
                }
            }
        }
    }
    public void validate() throws InvalidGrammarException {
        validate(START_SYMBOL.getReference());
    }

}
