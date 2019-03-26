package net.instant.tools.console_client;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.instant.tools.console_client.cli.CLI;
import net.instant.tools.console_client.gui.GUIClient;
import net.instant.util.argparse.Argument;
import net.instant.util.argparse.ArgumentParser;
import net.instant.util.argparse.Flag;
import net.instant.util.argparse.ParseResult;
import net.instant.util.argparse.ParsingException;
import net.instant.util.argparse.ValueOption;
import net.instant.util.argparse.ValueProcessor;

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

        public Flag createOption() {
            return Flag.make(optionName, optionLetter, optionDescription);
        }

        public static Mode selectFromArguments(Map<String, String> arguments,
                Mode defaultMode) throws ParsingException {
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
                throw new ParsingException("Multiple conflicting modes of " +
                    "operation specified");
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

    private static Map<String, String> unpackParseResult(ParseResult res) {
        Map<String, String> ret = new HashMap<String, String>();
        for (Map.Entry<ValueProcessor<?>, Object> ent :
             res.getData().entrySet()) {
            Object value = ent.getValue();
            String str = (value instanceof String) ? (String) value : null;
            ret.put(ent.getKey().getName(), str);
        }
        return ret;
    }

    public static void main(String[] args) {
        ArgumentParser p = new ArgumentParser("console-client", null,
            "Client of the Instant backend console (GUI- or " +
                "terminal-based).");
        p.addStandardOptions();
        for (Mode m : Mode.values()) p.add(m.createOption());
        p.add(ValueOption.of(String.class, "login", 'l',
            "Authenticate with the given username and a password read from " +
            "standard input.")
            .withPlaceholder("<USERNAME>"));
        p.add(Argument.of(String.class, "address",
            "The JMX endpoint to connect to (may also be a service URL).")
            .withPlaceholder("<HOST:PORT>"));
        Map<String, String> arguments;
        Mode mode;
        try {
            ParseResult res = p.parse(args);
            arguments = unpackParseResult(res);
            mode = Mode.selectFromArguments(arguments, Mode.GUI);
        } catch (ParsingException exc) {
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
