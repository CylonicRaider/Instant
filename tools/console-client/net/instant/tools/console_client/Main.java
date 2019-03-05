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
import net.instant.tools.console_client.util.ArgParser;

public class Main {

    private static Terminal createTerminal() {
        Terminal ret = ConsoleTerminal.getDefault();
        if (ret == null) ret = StreamPairTerminal.getDefault(true);
        return ret;
    }

    public static void doMain(Map<String, String> arguments)
            throws IOException {
        String address = arguments.get("address");
        Terminal term = createTerminal();
        JMXConnector connector;
        try {
            connector = Util.connectJMX(address, null);
        } catch (SecurityException exc) {
            String username = term.readLine("Username: ");
            String password = term.readPassword("Password: ");
            Map<String, Object> env = Util.prepareCredentials(username,
                                                              password);
            connector = Util.connectJMX(address, env);
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
        p.add(p.new Argument("address", "HOST:PORT",
            "The JMX endpoint to connect to (may also be a service URL)."));
        Map<String, String> arguments;
        try {
            arguments = p.parse(args);
        } catch (ArgParser.ParsingException exc) {
            System.err.println("ERROR: " + exc.getMessage());
            System.exit(1);
            // Should not happen.
            return;
        }
        try {
            doMain(arguments);
        } catch (IOException exc) {
            exc.printStackTrace();
            System.exit(2);
        }
    }

}
