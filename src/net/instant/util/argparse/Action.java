package net.instant.util.argparse;

public class Action implements Processor {

    public void parse(ArgumentSplitter source, ParseResultBuilder drain)
            throws ParsingException {
        ArgumentValue av = source.next(ArgumentSplitter.Mode.OPTIONS);
        if (av != null && av.getType() == ArgumentValue.Type.VALUE)
            throw new ParsingException("Unexpected option value for",
                                       "<anonymous>");
    }

    public String formatName() {
        return null;
    }

    public String formatUsage() {
        return null;
    }

    public HelpLine getHelpLine() {
        return null;
    }

}
