package net.instant.util;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class ListStringMatcher implements StringMatcher {

    private final List<StringMatcher> children;

    public ListStringMatcher() {
        children = new LinkedList<StringMatcher>();
    }

    public StringMatcher[] getChildren() {
        return children.toArray(new StringMatcher[children.size()]);
    }

    public StringMatcher add(StringMatcher child) {
        children.add(child);
        return child;
    }
    public void remove(StringMatcher child) {
        children.remove(child);
    }

    public StringMatcher add(Pattern pattern, String replacement) {
        return add(new DefaultStringMatcher(pattern, replacement, true));
    }
    public StringMatcher add(String pattern, String replacement) {
        return add(new DefaultStringMatcher(
            Pattern.compile(Pattern.quote(pattern)), replacement, false));
    }

    public String match(String input) {
        for (StringMatcher m : children) {
            String res = m.match(input);
            if (res != null) return res;
        }
        return null;
    }

}
