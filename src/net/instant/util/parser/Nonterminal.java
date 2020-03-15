package net.instant.util.parser;

public class Nonterminal extends AbstractSymbol {

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
