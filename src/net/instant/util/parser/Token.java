package net.instant.util.parser;

import net.instant.util.Formats;
import net.instant.util.LineColumnReader;
import net.instant.util.NamedValue;

public class Token implements NamedValue {

    private final String name;
    private final LineColumnReader.Coordinates position;
    private final String content;

    public Token(String name, LineColumnReader.Coordinates position,
                 String content) {
        if (position == null)
            throw new NullPointerException(
                "Token coordinates may not be null");
        if (content == null)
            throw new NullPointerException(
                "Token content may not be null");
        this.name = name;
        this.position = position;
        this.content = content;
    }

    public String toString() {
        String name = getName();
        return String.format("%s%s at %s",
            Formats.formatString(getContent()),
            ((name == null) ? "" : " (" + name + ")"),
            getPosition());
    }

    public boolean equals(Object other) {
        if (! (other instanceof Token)) return false;
        Token to = (Token) other;
        return (getPosition().equals(to.getPosition()) &&
                equalOrNull(getName(), to.getName()) &&
                getContent().equals(to.getContent()));
    }

    public int hashCode() {
        return hashCodeOrNull(getName()) ^ getPosition().hashCode() ^
            getContent().hashCode();
    }

    public String getName() {
        return name;
    }

    public LineColumnReader.Coordinates getPosition() {
        return position;
    }

    public String getContent() {
        return content;
    }

    public boolean matches(Symbol sym) {
        if (sym instanceof Nonterminal) {
            return ((Nonterminal) sym).getReference().equals(getName());
        } else if (sym instanceof Terminal) {
            return ((Terminal) sym).getPattern().matcher(getContent())
                .matches();
        } else {
            throw new IllegalArgumentException("Unrecognized symbol " +
                sym);
        }
    }
    public boolean matches(TokenPattern pat) {
        return (equalOrNull(getName(), pat.getName()) &&
                pat.matcher(getContent()).matches());
    }

    private static boolean equalOrNull(String a, String b) {
        return (a == null) ? (b == null) : a.equals(b);
    }
    private static int hashCodeOrNull(Object o) {
        return (o == null) ? 0 : o.hashCode();
    }

}
