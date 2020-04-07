package net.instant.util.argparse;

public class Flag extends BaseOption<ConstantStoreAction<Boolean>>
        implements ValueProcessor<Boolean> {

    public Flag(String name, Character shortName, String help,
                boolean value) {
        super(name, shortName, help,
              new ConstantStoreAction<Boolean>(value,
                                               new Committer<Boolean>()));
    }

    public Flag required() {
        super.required();
        return this;
    }
    public Flag optional() {
        super.optional();
        return this;
    }
    public Flag withComment(String comment) {
        super.withComment(comment);
        return this;
    }
    public Flag withChild(ConstantStoreAction<Boolean> c) {
        super.withChild(c);
        return this;
    }

    public boolean getValue() {
        return getChild().getValue();
    }

    public Flag setup() {
        getChild().getCommitter().setKey(this);
        return this;
    }

    public Flag inverse(String name, Character shortName) {
        String newHelp = "Inverse of " + formatName();
        Flag ret = new Flag("no-" + getName(), shortName, newHelp,
                            ! getValue());
        ret.getChild().getCommitter().setKey(this);
        return ret;
    }
    public Flag inverse() {
        return inverse("no-" + getName(), null);
    }

    public static Flag make(String name, Character shortName, String help) {
        return new Flag(name, shortName, help, true).setup();
    }

}
