package net.instant.tools.console_client;

import java.io.IOException;
import java.util.Map;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import net.instant.tools.console_client.cli.CLI;
import net.instant.tools.console_client.cli.ConsoleTerminal;
import net.instant.tools.console_client.cli.StreamPairTerminal;
import net.instant.tools.console_client.cli.SynchronousClient;
import net.instant.tools.console_client.cli.Terminal;
import net.instant.tools.console_client.jmx.Util;

public class Main {

    private static Terminal createTerminal() {
        Terminal ret = ConsoleTerminal.getDefault();
        if (ret == null) ret = StreamPairTerminal.getDefault(true);
        return ret;
    }

    private static void runREPLClosing(JMXConnector connector, Terminal term)
            throws IOException {
        MBeanServerConnection conn = connector.getMBeanServerConnection();
        try {
            SynchronousClient client = SynchronousClient.getNewDefault(conn);
            new CLI(client, term).run();
        } finally {
            connector.close();
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("USAGE: console-client HOST:PORT");
            System.exit(1);
        }
        Terminal term = createTerminal();
        try {
            JMXConnector connector;
            try {
                connector = Util.connectJMX(args[0], null);
            } catch (SecurityException exc) {
                String username = term.readLine("Username: ");
                String password = term.readPassword("Password: ");
                Map<String, Object> env = Util.prepareCredentials(username,
                                                                  password);
                connector = Util.connectJMX(args[0], env);
            }
            runREPLClosing(connector, term);
        } catch (IOException exc) {
            exc.printStackTrace();
            System.exit(2);
        }
    }

}
