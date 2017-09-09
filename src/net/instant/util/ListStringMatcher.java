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

    public void add(StringMatcher child) {
        children.add(child);
    }
    public void remove(StringMatcher child) {
        children.remove(child);
    }

    public StringMatcher add(Pattern pattern, String replacement,
                             boolean dynamic) {
        StringMatcher ret = new DefaultStringMatcher(pattern, replacement,
                                                     dynamic);
        add(ret);
        return ret;
    }
    public StringMatcher add(Pattern pattern, String replacement) {
        StringMatcher ret = new DefaultStringMatcher(pattern, replacement);
        add(ret);
        return ret;
    }
    public StringMatcher add(String pattern, String replacement) {
        StringMatcher ret = new DefaultStringMatcher(pattern, replacement);
        add(ret);
        return ret;
    }

    public String match(String input) {
        for (StringMatcher m : children) {
            String res = m.match(input);
            if (res != null) return res;
        }
        return null;
    }

}
