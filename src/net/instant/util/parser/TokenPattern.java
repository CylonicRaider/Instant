package net.instant.util.parser;

import java.util.Set;
import java.util.regex.Matcher;
import net.instant.util.LineColumnReader;
import net.instant.util.NamedValue;

public class TokenPattern implements NamedValue {

    private final String name;
    private final Grammar.Terminal symbol;

    public TokenPattern(String name, Grammar.Terminal symbol) {
        if (name == null)
            throw new NullPointerException(
                "TokenPattern name may not be null");
        if (symbol == null)
            throw new NullPointerException(
                "TokenPattern symbol may not be null");
        this.name = name;
        this.symbol = symbol;
    }

    public String toString() {
        return String.format("%s@%h[name=%s,symbol=%s]",
            getClass().getName(), this, getName(), getSymbol());
    }

    public boolean equals(Object other) {
        if (! (other instanceof TokenPattern)) return false;
        TokenPattern to = (TokenPattern) other;
        return (getName().equals(to.getName()) &&
                getSymbol().equals(to.getSymbol()));
    }

    public int hashCode() {
        return getName().hashCode() ^ getSymbol().hashCode();
    }

    public String getName() {
        return name;
    }

    public Grammar.Terminal getSymbol() {
        return symbol;
    }

    public Matcher matcher(CharSequence input) {
        return getSymbol().getPattern().matcher(input);
    }

    public Token createToken(LineColumnReader.Coordinates position,
                             String content) {
        return new Token(getName(), position, content);
    }

    public static TokenPattern create(String name,
            Set<Grammar.Production> prods) throws InvalidGrammarException {
        if (prods.size() == 0)
            throw new InvalidGrammarException(
                "Missing definition of token " + name);
        if (prods.size() > 1)
            throw new InvalidGrammarException(
                "Multiple productions for token " + name);
        Grammar.Production pr = prods.iterator().next();
        if (pr.getSymbols().size() != 1)
            throw new InvalidGrammarException("Token " +
                name + " definition must contain exactly one " +
                "nonterminal, got " + pr.getSymbols().size() + " instead");
        Grammar.Symbol sym = pr.getSymbols().get(0);
        if (! (sym instanceof Grammar.Terminal))
            throw new InvalidGrammarException("Token " + name +
                " definition may only contain terminals, got " + sym +
                " instead");
        return new TokenPattern(name, (Grammar.Terminal) sym);
    }

}
