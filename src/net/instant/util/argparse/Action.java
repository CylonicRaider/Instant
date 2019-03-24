package net.instant.util.argparse;

public class Action implements Processor {

    public void startParsing(ParseResultBuilder drain)
        throws ParsingException {}

    public void parse(ArgumentSplitter source, ParseResultBuilder drain)
            throws ParsingException {
        ArgumentSplitter.ArgValue av = source.peek(
            ArgumentSplitter.Mode.OPTIONS);
        if (av != null && av.getType() ==
                ArgumentSplitter.ArgType.VALUE)
            throw new ParsingException("Unexpected option value for",
                                       "<anonymous>");
    }

    public void finishParsing(ParseResultBuilder drain)
        throws ParsingException {}

    public String getName() {
        return null;
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
