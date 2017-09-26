package net.instant.util.argparse;

public abstract class ActionOption extends Option<Void> implements Runnable {

    public ActionOption(String name, Character shortname, String help) {
        super(name, shortname, help);
    }

    public OptionValue<Void> process(ArgumentValue v, ArgumentSplitter s)
            throws ParseException {
        ArgumentValue n = s.next(ArgumentSplitter.Mode.OPTIONS);
        if (n != null && n.getType() == ArgumentValue.Type.VALUE)
            throw new ParseException("Option --" + getName() +
                " does not take arguments");
        s.pushback(n);
        run();
        return null;
    }

    public String formatArguments() {
        return null;
    }

}
