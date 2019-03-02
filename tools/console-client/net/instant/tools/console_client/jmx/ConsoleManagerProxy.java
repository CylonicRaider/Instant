package net.instant.tools.console_client.jmx;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public class ConsoleManagerProxy extends JMXObjectProxy {

    public static final ObjectName DEFAULT_OBJECT_NAME = createObjectName(
        "net.instant.console:type=BackendConsoleManager");

    private static final String[] P_GET_CONSOLE = { int.class.getName() };

    public ConsoleManagerProxy(MBeanServerConnection connection,
                               ObjectName objectName) {
        super(connection, objectName);
    }

    public int[] listConsoles() {
        return invokeMethod("listConsoles", null, null, int[].class);
    }

    public ConsoleProxy getConsole(int id) {
        ObjectName res = invokeMethod("getConsole", new Object[] { id },
                                      P_GET_CONSOLE, ObjectName.class);
        return new ConsoleProxy(getConnection(), res);
    }

    public ConsoleProxy newConsole() {
        ObjectName res = invokeMethod("newConsole", null, null,
                                      ObjectName.class);
        return new ConsoleProxy(getConnection(), res);
    }

    public static ConsoleManagerProxy getDefault(MBeanServerConnection conn) {
        return new ConsoleManagerProxy(conn, DEFAULT_OBJECT_NAME);
    }

}
