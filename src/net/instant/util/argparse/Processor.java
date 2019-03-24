package net.instant.util.argparse;

public interface Processor {

    String getName();

    String formatName();

    String formatUsage();

    HelpLine getHelpLine();

    void startParsing(ParseResultBuilder drain) throws ParsingException;

    void parse(ArgumentSplitter source, ParseResultBuilder drain)
        throws ParsingException;

    void finishParsing(ParseResultBuilder drain) throws ParsingException;

}
