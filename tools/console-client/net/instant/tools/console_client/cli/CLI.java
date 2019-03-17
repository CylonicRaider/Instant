package net.instant.tools.console_client.cli;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import net.instant.tools.console_client.jmx.Util;

public class CLI implements Runnable {

    public static class Abort extends Exception {

        public Abort(String message) {
            super(message);
        }
        public Abort(String message, Throwable cause) {
            super(message, cause);
        }

    }

    public static final String PROMPT = "> ";

    private final SynchronousClient client;
    private final Terminal term;

    public CLI(SynchronousClient client, Terminal term) {
        this.client = client;
        this.term = term;
    }

    public SynchronousClient getClient() {
        return client;
    }

    public Terminal getTerminal() {
        return term;
    }

    public void run() {
        try {
            for (;;) {
                for (;;) {
                    String block = client.readOutputBlock();
                    if (block == null) break;
                    term.write(block);
                }
                if (client.isAtEOF()) break;
                String command = term.readLine(PROMPT);
                if (command == null) {
                    term.write("\n");
                    break;
                }
                client.submitCommand(command);
            }
        } catch (InterruptedException exc) {
            /* NOP */
        } finally {
            client.close();
        }
    }

    public static Terminal createDefaultTerminal(boolean isBatch) {
        Terminal ret = ConsoleTerminal.getDefault();
        if (ret == null) ret = StreamPairTerminal.getDefault(! isBatch);
        return ret;
    }

    public static void runDefault(Map<String, String> arguments,
                                  boolean isBatch, Terminal term)
            throws Abort, IOException {
        String address = arguments.get("address");
        if (address == null)
            throw new Abort("Missing connection address");
        String username = arguments.get("login");
        String password = null;
        Map<String, Object> env = new HashMap<String, Object>();
        /* The code below tries to concisely implement these behaviors:
         * - In batch mode, there is exactly one connection attempt which
         *   authenticates the user if-and-only-if a username is specified on
         *   the command line.
         * - In interactive mode, if the first connection attempt fails,
         *   another is made with credentials supplied.
         * - Failing a connection attempt with credentials provided is
         *   fatal. */
        boolean addCredentials = (isBatch && username != null);
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
                if (isBatch || addCredentials)
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
    public static void runDefault(Map<String, String> arguments,
                                  boolean isBatch) throws Abort, IOException {
        runDefault(arguments, isBatch, createDefaultTerminal(isBatch));
    }

}
