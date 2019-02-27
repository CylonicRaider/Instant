package net.instant.tools.console_client;

import java.io.Console;
import java.io.IOException;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import net.instant.tools.console_client.jmx.ConsoleProxy;
import net.instant.tools.console_client.jmx.Util;

public class Main {

    private static void runREPLClosing(JMXConnector connector)
            throws IOException {
        Console lcon = System.console();
        MBeanServerConnection conn = connector.getMBeanServerConnection();
        ConsoleProxy rcon = null;
        try {
            rcon = ConsoleProxy.getNewDefault(conn);
            for (;;) {
                String command = lcon.readLine("> ");
                if (command == null) {
                    lcon.printf("%n");
                    break;
                }
                String result = rcon.runCommand(command);
                if (result != null && ! result.isEmpty())
                    lcon.printf("%s%n", result);
            }
        } finally {
            if (rcon != null) rcon.close();
            connector.close();
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("USAGE: console-client HOST:PORT");
            System.exit(1);
        }
        if (System.console() == null) {
            System.err.println("ERROR: Must have a console");
            System.exit(2);
        }
        try {
            JMXConnector connector = Util.connectJMX(args[0]);
            runREPLClosing(connector);
        } catch (IOException exc) {
            exc.printStackTrace();
        }
    }

}
