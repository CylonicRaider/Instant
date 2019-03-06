package net.instant.tools.console_client;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import net.instant.tools.console_client.cli.CLI;
import net.instant.tools.console_client.cli.ConsoleTerminal;
import net.instant.tools.console_client.cli.StreamPairTerminal;
import net.instant.tools.console_client.cli.SynchronousClient;
import net.instant.tools.console_client.cli.Terminal;
import net.instant.tools.console_client.jmx.Util;
import net.instant.tools.console_client.util.ArgParser;

public class Main {

    public static class Abort extends Exception {

        public Abort(String message) {
            super(message);
        }
        public Abort(String message, Throwable cause) {
            super(message, cause);
        }

    }

    public enum Mode {
        CLI_INTERACTIVE("interactive", 'I',
            "Interactively prompt for commands from console."),
        CLI_BATCH("batch", 'B',
            "Deterministically read commands from standard input.");

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

    private static Terminal createTerminal(Mode mode) {
        Terminal ret = ConsoleTerminal.getDefault();
        if (ret == null)
            ret = StreamPairTerminal.getDefault(mode == Mode.CLI_INTERACTIVE);
        return ret;
    }

    public static void doMain(Map<String, String> arguments, Mode mode)
            throws Abort, IOException {
        String address = arguments.get("address");
        if (address == null)
            throw new NullPointerException("Missing connection address");
        String username = arguments.get("login");
        String password = null;
        Terminal term = createTerminal(mode);
        Map<String, Object> env = new HashMap<String, Object>();
        /* The code below tries to concisely implement these behaviors:
         * - In batch mode, there is exactly one connection attempt which
         *   authenticates the user if-and-only-if a username is specified on
         *   the command line.
         * - In interactive mode, if the first connection attempt fails,
         *   another is made with credentials supplied.
         * - Failing a connection attempt with credentials provided is
         *   fatal. */
        boolean addCredentials = (mode == Mode.CLI_BATCH && username != null);
        JMXConnector connector;
        for (;;) {
            if (addCredentials) {
                if (username == null)
                    username = term.readLine("Username: ");
                if (password == null)
                    password = term.readPassword("Password: ");
                Util.insertCredentials(env, username, password);
            }
            try {
                connector = Util.connectJMX(address, env);
                break;
            } catch (SecurityException exc) {
                if (mode == Mode.CLI_BATCH || addCredentials)
                    throw new Abort("Security error: " + exc.getMessage(),
                                    exc);
            }
            addCredentials = true;
        }
        MBeanServerConnection conn = connector.getMBeanServerConnection();
        try {
            SynchronousClient client = SynchronousClient.getNewDefault(conn);
            new CLI(client, term).run();
        } finally {
            connector.close();
        }
    }

    public static void main(String[] args) {
        ArgParser p = new ArgParser("console-client");
        p.add(p.new HelpOption());
        p.add(Mode.CLI_INTERACTIVE.createOption(p));
        p.add(Mode.CLI_BATCH.createOption(p));
        p.add(p.new Option("login", 'l', "USERNAME",
            "Authenticate with the given username and a password read from " +
            "standard input."));
        p.add(p.new Argument("address", "HOST:PORT",
            "The JMX endpoint to connect to (may also be a service URL)."));
        Map<String, String> arguments;
        Mode mode;
        try {
            arguments = p.parse(args);
            mode = Mode.selectFromArguments(arguments, Mode.CLI_INTERACTIVE);
        } catch (ArgParser.ParsingException exc) {
            System.err.println("ERROR: " + exc.getMessage());
            System.exit(1);
            // Should not happen.
            return;
        }
        try {
            doMain(arguments, mode);
        } catch (Abort exc) {
            System.err.println("ERROR: " + exc.getMessage());
            System.exit(2);
        } catch (IOException exc) {
            exc.printStackTrace();
            System.exit(2);
        }
    }

}
