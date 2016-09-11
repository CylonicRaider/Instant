package net.instant.plugins;

public class GroupConstraintLoopException extends PluginConflictException {

    private final PluginGroup g;
    private final Constraint c;

    public GroupConstraintLoopException(PluginGroup g, Constraint c) {
        super(formatMessage(g, c));
        this.g = g;
        this.c = c;
    }

    public PluginGroup getGroup() {
        return g;
    }
    public Constraint getConstraint() {
        return c;
    }

    protected static String formatMessage(PluginGroup g, Constraint c) {
        return String.format("%s %s %1$s", g.getNames(), c);
    }

}
