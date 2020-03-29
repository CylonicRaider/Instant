package net.instant.util.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.instant.util.Formats;
import net.instant.util.NamedMap;
import net.instant.util.NamedSet;
import net.instant.util.NamedValue;

public class Grammar implements GrammarView {

    public interface Symbol {

        /* Do not generate an own parsing tree node for this symbol. */
        int SYM_INLINE = 1;
        /* Discard any parsing tree nodes stemming from this symbol (may be
         * overridden on a per-parser basis). */
        int SYM_DISCARD = 2;
        /* Optionally skip this symbol (regular expression x?).
         * If the symbol is not matched, no parse tree is generated for it. */
        int SYM_OPTIONAL = 4;
        /* Permit repetitions of this symbol (regular expression x+).
         * Multiple matches generate adjacent parse subtrees. Combine with
         * SYM_OPTIONAL to permit any amount of repetitions (regular
         * expression x*). */
        int SYM_REPEAT = 8;

        /* All known flags combined. */
        int SYM_ALL = SYM_INLINE | SYM_DISCARD | SYM_OPTIONAL | SYM_REPEAT;

        int getFlags();

        Symbol withFlags(int newFlags);

    }

    public static abstract class AbstractSymbol implements Symbol {

        private int flags;

        public AbstractSymbol(int flags) {
            this.flags = flags;
        }

        public String toString() {
            return Grammars.formatWithSymbolFlags(toStringBase(), getFlags());
        }

        public boolean equals(Object other) {
            if (! (other instanceof AbstractSymbol)) return false;
            AbstractSymbol co = (AbstractSymbol) other;
            return (getFlags() == co.getFlags() && matches(co) &&
                    co.matches(this));
        }

        public int hashCode() {
            return hashCodeBase() ^ getFlags();
        }

        protected abstract String toStringBase();

        protected abstract boolean matches(AbstractSymbol other);

        protected abstract int hashCodeBase();

        public int getFlags() {
            return flags;
        }

    }

    public static class Nonterminal extends AbstractSymbol {

        private final String reference;

        public Nonterminal(String reference, int flags) {
            super(flags);
            if (reference == null)
                throw new NullPointerException(
                    "Nonterminal reference may not be null");
            this.reference = reference;
        }

        protected String toStringBase() {
            return getReference();
        }

        protected boolean matches(AbstractSymbol other) {
            return ((other instanceof Nonterminal) &&
                getReference().equals(((Nonterminal) other).getReference()));
        }

        protected int hashCodeBase() {
            return reference.hashCode();
        }

        public String getReference() {
            return reference;
        }

        public Nonterminal withFlags(int newFlags) {
            if (newFlags == getFlags()) return this;
            return new Nonterminal(getReference(), newFlags);
        }

    }

    public static class Terminal extends AbstractSymbol {

        private final Pattern pattern;

        public Terminal(Pattern pattern, int flags) {
            super(flags);
            if (pattern == null)
                throw new NullPointerException(
                    "Terminal pattern may not be null");
            this.pattern = pattern;
        }

        protected String toStringBase() {
            return Formats.formatPattern(getPattern());
        }

        protected boolean matches(AbstractSymbol other) {
            return ((other instanceof Terminal) &&
                    patternsEqual(getPattern(),
                                  ((Terminal) other).getPattern()));
        }

        protected int hashCodeBase() {
            return patternHashCode(pattern);
        }

        public Pattern getPattern() {
            return pattern;
        }

        public int getMatchRank() {
            return 0;
        }

        public Terminal withFlags(int newFlags) {
            if (newFlags == getFlags()) return this;
            return new Terminal(getPattern(), newFlags);
        }

        public static boolean patternsEqual(Pattern a, Pattern b) {
            // HACK: Assuming the Pattern API does not change in incompatible
            //       ways...
            if (a == null) return (b == null);
            if (b == null) return (a == null);
            return (a.pattern().equals(b.pattern()) &&
                    a.flags() == b.flags());
        }

        public static int patternHashCode(Pattern pat) {
            if (pat == null) return 0;
            return pat.pattern().hashCode() ^ pat.flags();
        }

    }

    public static class FixedTerminal extends Terminal {

        private final String content;

        public FixedTerminal(String content, int flags) {
            super(Pattern.compile(Pattern.quote(content)), flags);
            if (content == null)
                throw new NullPointerException(
                    "FixedTerminal content may not be null");
            this.content = content;
        }

        protected String toStringBase() {
            return Formats.formatString(getContent());
        }

        protected boolean matches(AbstractSymbol other) {
            return (other instanceof FixedTerminal) &&
                    getContent().equals(((FixedTerminal) other).getContent());
        }

        protected int hashCodeBase() {
            return content.hashCode();
        }

        public String getContent() {
            return content;
        }

        public int getMatchRank() {
            return 100;
        }

        public FixedTerminal withFlags(int newFlags) {
            if (newFlags == getFlags()) return this;
            return new FixedTerminal(getContent(), newFlags);
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
        public Production(String name, Symbol... symbols) {
            this(name, Arrays.asList(symbols));
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
