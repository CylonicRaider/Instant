package net.instant.util.argparse;

public abstract class ActionOption extends Option<Void> implements Runnable {

    public ActionOption(String name) {
        super(name, 0, false, false, null);
    }

    public String getPlaceholder(int index) {
        return null;
    }
    public OptionValue<Void> parse(OptionValue<Void> old, String[] args) {
        run();
        return wrap(null);
    }

}
