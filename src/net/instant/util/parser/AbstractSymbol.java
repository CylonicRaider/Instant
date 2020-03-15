package net.instant.util.parser;

public abstract class AbstractSymbol implements Symbol {

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
