package net.instant.util.parser;

import java.util.regex.Pattern;
import net.instant.util.Formats;

public class FixedTerminal extends Terminal {

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
