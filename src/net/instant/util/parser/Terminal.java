package net.instant.util.parser;

import java.util.regex.Pattern;
import net.instant.util.Formats;

public class Terminal extends AbstractSymbol {

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
                patternsEqual(getPattern(), ((Terminal) other).getPattern()));
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
