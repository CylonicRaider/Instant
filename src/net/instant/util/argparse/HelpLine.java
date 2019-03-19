package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

public class HelpLine {

    private String name;
    private String separator;
    private String params;
    private String description;
    private final List<String> addenda;

    public HelpLine(String name, String separator, String params,
                    String description) {
        this.name = adaptNull(name);
        this.separator = adaptNull(separator);
        this.params = adaptNull(params);
        this.description = adaptNull(description);
        this.addenda = new ArrayList<String>();
    }
    public HelpLine(String name, String params, String description) {
        this(name, "", params, description);
    }

    public String getName() {
        return name;
    }
    public void setName(String n) {
        name = adaptNull(n);
    }

    public String getSeparator() {
        return separator;
    }
    public void setSeparator(String sep) {
        separator = adaptNull(sep);
    }

    public String getParams() {
        return params;
    }
    public void setParams(String p) {
        params = adaptNull(p);
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String desc) {
        description = adaptNull(desc);
    }

    public List<String> getAddenda() {
        return addenda;
    }
    public void addAddendum(String entry) {
        addenda.add(entry);
    }

    private static String adaptNull(String s) {
        return (s == null) ? "" : s;
    }

    private static String leftpadFormat(int width) {
        // "%-0s" is, of course, a syntax error, so we have to special-case
        // it, and provide this method.
        return (width == 0) ? "%s" : "%-" + width + "s";
    }

    public static void format(List<HelpLine> lines, Formatter f) {
        /* Compute column widths. */
        int nameWidth = 0, paramWidth = 0;
        for (HelpLine l : lines) {
            nameWidth = Math.max(nameWidth, l.getName().length() +
                                            l.getSeparator().length());
            paramWidth = Math.max(paramWidth, l.getParams().length());
        }
        /* Prepare format strings */
        // It will take until Java 11 for string repetition (which would make
        // all of this much more elegant) to appear in the standard library...
        String lineStartFormat = leftpadFormat(nameWidth);
        String lineMidFormat = String.format("%s%s: %%s",
            ((nameWidth != 0 && paramWidth != 0) ? " " : ""),
            leftpadFormat(paramWidth));
        /* Print columns */
        boolean firstLine = true;
        for (HelpLine l : lines) {
            if (firstLine) {
                firstLine = false;
            } else {
                f.format("%n");
            }
            String sep = l.getSeparator();
            if (sep.isEmpty()) {
                f.format(lineStartFormat, l.getName());
            } else {
                f.format(leftpadFormat(nameWidth - sep.length()) + "%s",
                         l.getName(), sep);
            }
            f.format(lineMidFormat, l.getParams(), l.getDescription());
            if (! l.getAddenda().isEmpty()) {
                f.format(" (");
                boolean first = true;
                for (String a : l.getAddenda()) {
                    if (a == null) continue;
                    f.format(((first) ? "%s" : ", %s"), a);
                    first = false;
                }
                f.format(")");
            }
        }
        /* Done */
        f.flush();
    }

}
