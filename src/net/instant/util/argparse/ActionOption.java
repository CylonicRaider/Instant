package net.instant.util.argparse;

public abstract class ActionOption extends Option<Void> {

    public ActionOption(String name, Character shortname, String help) {
        super(name, shortname, help);
    }

    public OptionValue<Void> process(ArgumentParser p, ArgumentValue v,
                                     ArgumentSplitter s) {
        run(p);
        return null;
    }

    public String formatArguments() {
        return null;
    }

    protected abstract void run(ArgumentParser p);

}
