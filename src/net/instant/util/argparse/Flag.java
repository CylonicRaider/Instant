package net.instant.util.argparse;

public class Flag extends FlagOption<Boolean> {

    public Flag(String name, Character shortName, String help) {
        super(name, shortName, help);
        setValue(true);
    }

    public Flag inverse() {
        String newHelp = "Inverse of --" + getName();
        Flag ret = new Flag("no-" + getName(), getShortName(), newHelp) {
            public OptionValue<Boolean> wrap(Boolean value) {
                return new OptionValue<Boolean>(Flag.this, value);
            }
        };
        ret.setValue(! getValue());
        return ret;
    }

}
