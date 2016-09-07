package net.instant.plugins;

public class PluginConstraintAttribute extends StringSetAttribute {

    private final Constraint constraint;

    public PluginConstraintAttribute(String name, Constraint constraint) {
        super(name);
        this.constraint = constraint;
    }

    public Constraint getConstraint() {
        return constraint;
    }

}
