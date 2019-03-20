package net.instant.util.argparse;

public interface Processor {

    void parse(ArgumentSplitter source, ParseResultBuilder drain)
        throws ParsingException;

    String getName();

    String getUsage();

    HelpLine getHelp();

}
