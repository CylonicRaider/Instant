package net.instant.plugins;

public class MutualPluginConflictException extends PluginConflictException {

    private final Plugin a;
    private final Plugin b;
    private final Constraint ab;
    private final Constraint ba;

    public MutualPluginConflictException(Plugin a, Plugin b, Constraint ab,
                                         Constraint ba) {
        super(formatMessage(a, b, ab, ba));
        this.a = a;
        this.b = b;
        this.ab = ab;
        this.ba = ba;
    }
    public MutualPluginConflictException(Plugin a, Plugin b) {
        this(a, b, a.getConstraint(b), b.getConstraint(a));
    }

    public Plugin getA() {
        return a;
    }
    public Plugin getB() {
        return b;
    }
    public Constraint getAB() {
        return ab;
    }
    public Constraint getBA() {
        return ba;
    }

    protected static String formatMessage(Plugin a, Plugin b, Constraint ab,
                                          Constraint ba) {
        return String.format("%s %s %s; %3$s %s %1$s",
                             a.getName(), ab, b.getName(), ba);
    }

}
