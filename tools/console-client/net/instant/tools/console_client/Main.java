package net.instant.tools.console_client;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.instant.tools.console_client.cli.CLI;
import net.instant.tools.console_client.gui.GUIClient;
import net.instant.tools.console_client.util.ArgParser;

public class Main {

    public enum Mode {
        CLI_INTERACTIVE("interactive", 'I',
            "Interactively prompt for commands from console."),
        CLI_BATCH("batch", 'B',
            "Deterministically read commands from standard input."),
        GUI("gui", 'G',
            "Show a graphical user interface.");

        private final String optionName;
        private final char optionLetter;
        private final String optionDescription;

        private Mode(String name, char letter, String desc) {
            optionName = name;
            optionLetter = letter;
            optionDescription = desc;
        }

        public String getOptionName() {
            return optionName;
        }

        public char getOptionLetter() {
            return optionLetter;
        }

        public String getOptionDescription() {
            return optionDescription;
        }

        public ArgParser.Option createOption(ArgParser p) {
            return p.new Option(optionName, optionLetter, null,
                                optionDescription);
        }

        public static Mode selectFromArguments(Map<String, String> arguments,
                Mode defaultMode) throws ArgParser.ParsingException {
            Set<Mode> selectedModes = EnumSet.noneOf(Mode.class);
            for (Mode m : values()) {
                if (arguments.containsKey(m.getOptionName()))
                    selectedModes.add(m);
            }
            if (selectedModes.size() == 0) {
                return defaultMode;
            } else if (selectedModes.size() == 1) {
                return selectedModes.iterator().next();
            } else {
                throw new ArgParser.ParsingException("Multiple conflicting " +
                    "modes of operation specified");
            }
        }

    };

    public static void doMain(Map<String, String> arguments, Mode mode)
            throws CLI.Abort, IOException {
        if (mode == Mode.GUI) {
            GUIClient.showDefault(arguments);
        } else {
            CLI.runDefault(arguments, (mode == Mode.CLI_BATCH));
        }
    }

    public static void main(String[] args) {
        ArgParser p = new ArgParser("console-client");
        p.add(p.new HelpOption());
        for (Mode m : Mode.values()) p.add(m.createOption(p));
        p.add(p.new Option("login", 'l', "USERNAME",
            "Authenticate with the given username and a password read from " +
            "standard input."));
        p.add(p.new Argument("address", "HOST:PORT", true,
            "The JMX endpoint to connect to (may also be a service URL)."));
        Map<String, String> arguments;
        Mode mode;
        try {
            arguments = p.parse(args);
            mode = Mode.selectFromArguments(arguments, Mode.GUI);
        } catch (ArgParser.ParsingException exc) {
            System.err.println("ERROR: " + exc.getMessage());
            System.exit(1);
            // Should not happen.
            return;
        }
        try {
            doMain(arguments, mode);
        } catch (CLI.Abort exc) {
            System.err.println("ERROR: " + exc.getMessage());
            System.exit(2);
        } catch (IOException exc) {
            exc.printStackTrace();
            System.exit(2);
        }
    }

}
