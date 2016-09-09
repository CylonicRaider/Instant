package net.instant.plugins;

public class PluginGroupConflictException extends PluginConflictException {

    private final PluginGroup group;
    private final Plugin plugin;
    private final Constraint constraint;

    public PluginGroupConflictException(PluginGroup g, Plugin p,
                                        Constraint c) {
        super(formatMessage(g, p, c));
        this.group = g;
        this.plugin = p;
        this.constraint = c;
    }

    public PluginGroup getGroup() {
        return group;
    }
    public Plugin getPlugin() {
        return plugin;
    }
    public Constraint getConstraint() {
        return constraint;
    }

    protected static String formatMessage(PluginGroup g, Plugin p,
                                          Constraint c) {
        return String.format("%s %s %s; %3$s must be added to %1$s",
                             g.getNames(), p.getName(), c);
    }

}
