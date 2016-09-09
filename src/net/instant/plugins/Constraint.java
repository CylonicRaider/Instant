package net.instant.plugins;

import java.util.HashMap;
import java.util.Map;

public enum Constraint {

    BREAKS(false, false, false),
    AFTER(false, false, true),
    WITH(false, true, false),
    NOT_BEFORE(false, true, true),
    BEFORE(true, false, false),
    NOT_WITH(true, false, true),
    NOT_AFTER(true, true, false),
    INDIFFERENT_OF(true, true, true);

    private static final Constraint[] constraints;
    private static final Constraint[] flips;

    static {
        constraints = values();
        flips = new Constraint[] {
            BREAKS, BEFORE, WITH, NOT_AFTER,
            AFTER, NOT_WITH, NOT_BEFORE, INDIFFERENT_OF
        };
    }

    private final boolean before, with, after;

    private Constraint(boolean b, boolean w, boolean a) {
        before = b;
        with = w;
        after = a;
    }

    public boolean isBefore() {
        return before;
    }
    public boolean isWith() {
        return with;
    }
    public boolean isAfter() {
        return after;
    }

    public Constraint not() {
        return constraints[ordinal() ^ 7];
    }
    public Constraint and(Constraint other) {
        return constraints[ordinal() & other.ordinal()];
    }
    public Constraint or(Constraint other) {
        return constraints[ordinal() | other.ordinal()];
    }
    public Constraint xor(Constraint other) {
        return constraints[ordinal() ^ other.ordinal()];
    }

    public Constraint flip() {
        return flips[ordinal()];
    }

    public boolean isCompatible(Constraint other) {
        return ((ordinal() & other.ordinal()) != 0);
    }

    public static Constraint get(String name) {
        Constraint ret = valueOf(name.toUpperCase().replace("-", "_"));
        if (ret == null)
            throw new IllegalArgumentException("Bad constraint name");
        return ret;
    }
    public static Constraint get(boolean b, boolean w, boolean a) {
        return constraints[(b ? 4 : 0) | (w ? 2 : 0) | (a ? 1 : 0)];
    }
    public static Constraint get(int ord) {
        if (ord < 0 || ord >= 8)
            throw new IllegalArgumentException("Bad constraint ordinal");
        return constraints[ord];
    }

}
