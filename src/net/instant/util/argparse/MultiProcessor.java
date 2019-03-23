package net.instant.util.argparse;

import java.util.List;

public interface MultiProcessor extends Processor {

    String formatDescription();

    List<HelpLine> getAllHelpLines();

}
