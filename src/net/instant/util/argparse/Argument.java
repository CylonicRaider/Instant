package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.List;

public class Argument {

    private final String value;
    private final boolean option;
    private final boolean attached;

    public Argument(String value, boolean isOption, boolean isAttached) {
        this.value = value;
        this.option = isOption;
        this.attached = isAttached;
    }

    public String toString() {
        return getClass().getName() + "[" + ((isOption()) ? "--" : "") +
            getValue() + "]";
    }

    public String getValue() {
        return value;
    }
    public boolean isOption() {
        return option;
    }
    public boolean isAttached() {
        return attached;
    }

    public static void walk(String[] args, Visitor v) throws ParseException {
        boolean positional = false, nextPos = false;
        for (String a : args) {
            if (positional || nextPos) {
                /* Fall through to bottom */
            } else if (a.equals("--")) {
                positional = true;
                continue;
            } else if (a.equals("-")) {
                /* Fall through */
            } else if (a.startsWith("-")) {
                int bi = (a.charAt(1) == '-') ? 2 : 1, eqi = a.indexOf("=");
                if (eqi == -1) {
                    nextPos = v.process(new Argument(a.substring(bi),
                                                     true, false));
                } else {
                    String optName = a.substring(bi, eqi);
                    if (! v.process(new Argument(optName, true, false)))
                        throw new ParseException("Option '" + optName +
                            "' does not take arguments");
                    nextPos = v.process(new Argument(a.substring(eqi + 1),
                                                     false, true));
                }
                continue;
            }
            nextPos = v.process(new Argument(a, false, false));
        }
        v.end();
    }

    public static void walkGroups(String[] args, GroupVisitor visitor)
            throws ParseException {
        final GroupVisitor v = visitor;
        walk(args, new Visitor() {

            private final List<Argument> accum = new ArrayList<Argument>();
            private int pending = 0;

            public boolean process(Argument arg) throws ParseException {
                accum.add(arg);
                if (pending == 0) {
                    pending = v.first(arg);
                } else if (pending > 0) {
                    pending--;
                }
                if (pending == 0) {
                    v.done(accum);
                    accum.clear();
                }
                return (pending != 0);
            }

            public void end() throws ParseException {
                v.end(accum);
                accum.clear();
            }

        });
    }

    public interface Visitor {

        boolean process(Argument arg) throws ParseException;

        void end() throws ParseException;

    }

    public interface GroupVisitor {

        int first(Argument arg) throws ParseException;

        void done(List<Argument> group) throws ParseException;

        void end(List<Argument> remaining) throws ParseException;

    }

}
