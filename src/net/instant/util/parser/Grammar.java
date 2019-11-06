package net.instant.util.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.instant.util.NamedMap;
import net.instant.util.NamedSet;
import net.instant.util.NamedValue;

public class Grammar implements GrammarView {

    public enum SymbolType { NONTERMINAL, TERMINAL, PATTERN_TERMINAL }

    public static class Symbol {

        private final SymbolType type;
        private final String content;
        private final int flags;
        private final Pattern pattern;

        public Symbol(SymbolType type, String content, int flags) {
            if (type == null)
                throw new NullPointerException("Symbol type may not be null");
            if (content == null)
                throw new NullPointerException(
                    "Symbol content may not be null");
            if ((flags & SYM_ALL_FLAGS) != 0)
                throw new IllegalArgumentException(
                    "Unknown Symbol flags specified");
            this.type = type;
            this.content = content;
            this.flags = flags;
            switch (type) {
                case NONTERMINAL:
                    this.pattern = null;
                    break;
                case TERMINAL:
                    this.pattern = Pattern.compile(Pattern.quote(content));
                    break;
                case PATTERN_TERMINAL:
                    this.pattern = Pattern.compile(content);
                    break;
                default:
                    throw new AssertionError("This should not happen?!");
            }
        }

        public String toString() {
            return String.format("%s@%h[type=%s,content=%s,flags=%s]",
                                 getClass().getName(), this, getType(),
                                 getContent(), getFlags());
        }

        public boolean equals(Object other) {
            if (! (other instanceof Symbol)) return false;
            Symbol so = (Symbol) other;
            return (getType() == so.getType() &&
                    getFlags() == so.getFlags() &&
                    getContent().equals(so.getContent()));
        }

        public int hashCode() {
            return getType().hashCode() ^ getContent().hashCode() ^
                getFlags();
        }

        public SymbolType getType() {
            return type;
        }

        public String getContent() {
            return content;
        }

        public int getFlags() {
            return flags;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public boolean matches(String input) {
            Pattern pat = getPattern();
            if (pat == null) return false;
            return pat.matcher(input).matches();
        }

    }

    public static class Production implements NamedValue {

        public static final Pattern NAME_PATTERN = Pattern.compile(
            "[a-zA-Z$_-][A-Za-z0-9$_-]*");

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

        public String toString() {
            return String.format("%s@%h[name=%s,symbols=%s]",
                getClass().getName(), this, getName(), getSymbols());
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

    public static final int SYM_INLINE = 1;
    public static final int SYM_NONESSENTIAL = 2;

    public static final int SYM_ALL_FLAGS = 3;

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
    public Grammar(Production... productions) {
        this();
        for (Production p : productions) addProduction(p);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName());
        sb.append('@');
        sb.append(Integer.toHexString(hashCode()));
        sb.append('[');
        boolean first = true;
        for (Set<Production> ps : productions.values()) {
            for (Production p : ps) {
                if (first) {
                    first = true;
                } else {
                    sb.append(',');
                }
                sb.append(p);
            }
        }
        sb.append(']');
        return sb.toString();
    }

    public Set<String> getProductionNames() {
        return Collections.unmodifiableSet(productions.keySet());
    }
    public Set<Production> getProductions(String name) {
        return Collections.unmodifiableSet(productions.get(name));
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
                    if (s.getType() == SymbolType.NONTERMINAL &&
                            ! checkProductions(s.getContent()))
                        throw new InvalidGrammarException("Symbol " + s +
                            " referencing a nonexistent production");
                }
            }
        }
    }
    public void validate() throws InvalidGrammarException {}

}
