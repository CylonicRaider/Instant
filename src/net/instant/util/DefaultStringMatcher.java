package net.instant.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultStringMatcher implements StringMatcher {

    public static final Pattern GROUPING_RE =
        Pattern.compile("\\\\([0-9]+|\\{[0-9]+\\}|[^0-9])");

    private final Pattern pattern;
    private final String replacement;
    private final boolean dynamic;

    public DefaultStringMatcher(Pattern pattern, String replacement,
                                boolean dynamic) {
        this.pattern = pattern;
        this.replacement = replacement;
        this.dynamic = dynamic;
    }

    public Pattern getPattern() {
        return pattern;
    }
    public String getReplacement() {
        return replacement;
    }
    public boolean isDynamic() {
        return dynamic;
    }

    public String match(String input) {
        Matcher m = pattern.matcher(input);
        if (m.matches()) {
            if (dynamic) {
                return expand(m, replacement);
            } else {
                return replacement;
            }
        } else {
            return null;
        }
    }

    public static String expand(Matcher m, String repl) {
        Matcher rm = GROUPING_RE.matcher(repl);
        StringBuffer sb = new StringBuffer();
        while (rm.find()) {
            String g = rm.group(1);
            if (g.equals("\\")) {
                rm.appendReplacement(sb, "\\\\");
            } else if (g.matches("[0-9]+")) {
                rm.appendReplacement(sb, escapeExpand(m.group(
                    Integer.parseInt(g))));
            } else if (g.matches("\\{[0-9]+\\}")) {
                rm.appendReplacement(sb, escapeExpand(m.group(
                    Integer.parseInt(g.substring(1, g.length() - 1)))));
            } else {
                throw new RuntimeException("Invalid replacement!");
            }
        }
        rm.appendTail(sb);
        return sb.toString();
    }
    private static String escapeExpand(String s) {
        return s.replaceAll("[\\\\$]", "\\\\$0");
    }

}
