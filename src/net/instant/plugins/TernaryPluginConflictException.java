package net.instant.plugins;

public class TernaryPluginConflictException extends PluginConflictException {

    private final PluginGroup a;
    private final Plugin b;
    private final Plugin c;
    private final Constraint ac;
    private final Constraint bc;

    public TernaryPluginConflictException(PluginGroup a, Plugin b, Plugin c,
                                          Constraint ac, Constraint bc) {
        super(formatMessage(a, b, c, ac, bc));
        this.a = a;
        this.b = b;
        this.c = c;
        this.ac = ac;
        this.bc = bc;
    }

    public PluginGroup getA() {
        return a;
    }
    public Plugin getB() {
        return b;
    }
    public Plugin getC() {
        return c;
    }
    public Constraint getAC() {
        return ac;
    }
    public Constraint getBC() {
        return bc;
    }

    protected static String formatMessage(PluginGroup a, Plugin b, Plugin c,
                                          Constraint ac, Constraint bc) {
        return String.format("%s %s %s; %s %s %3$s",
                             a.getNames(), ac, c.getName(), b.getName(), bc);
    }

}
