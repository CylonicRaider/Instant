package net.instant.util.argparse;

public class Flag extends Option<Boolean> {

    public Flag(String name, Character shortName, String help,
                boolean value) {
        super(name, shortName, help,
              new ConstantStoreAction<Boolean>(value,
                                               new Committer<Boolean>()));
        getAction().getCommitter().setKey(this);
    }

    private ConstantStoreAction<Boolean> getAction() {
        @SuppressWarnings("unchecked")
        ConstantStoreAction<Boolean> act =
            (ConstantStoreAction<Boolean>) getChild();
        return act;
    }

    public boolean getValue() {
        return getAction().getValue();
    }

    public Flag inverse(String name, Character shortName) {
        String newHelp = "Inverse of " + formatName();
        return new Flag("no-" + getName(), null, newHelp, ! getValue());
    }
    public Flag inverse() {
        return inverse("no-" + getName(), null);
    }

}
