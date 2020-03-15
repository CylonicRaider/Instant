package net.instant.util.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import net.instant.util.NamedValue;

public class Production implements NamedValue {

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
