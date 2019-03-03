package net.instant.tools.console_client;

import java.io.IOException;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import net.instant.tools.console_client.cli.CLI;
import net.instant.tools.console_client.cli.ConsoleTerminal;
import net.instant.tools.console_client.cli.StreamPairTerminal;
import net.instant.tools.console_client.cli.SynchronousClient;
import net.instant.tools.console_client.cli.Terminal;
import net.instant.tools.console_client.jmx.Util;

public class Main {

    private static void runREPLClosing(JMXConnector connector)
            throws IOException {
        MBeanServerConnection conn = connector.getMBeanServerConnection();
        try {
            SynchronousClient client = SynchronousClient.getNewDefault(conn);
            Terminal term = ConsoleTerminal.getDefault();
            if (term == null) term = StreamPairTerminal.getDefault();
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
        try {
            JMXConnector connector = Util.connectJMX(args[0]);
            runREPLClosing(connector);
        } catch (IOException exc) {
            exc.printStackTrace();
            System.exit(2);
        }
    }

}
