package net.instant.tools.console_client;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public class ConsoleProxy extends JMXObjectProxy {

    private final String[] HISTORY_ENTRY_PARAMS = { int.class.getName() };
    private final String[] RUN_COMMAND_PARAMS = { String.class.getName() };

    public ConsoleProxy(MBeanServerConnection connection,
                        ObjectName objectName) {
        super(connection, objectName);
    }

    public String historyEntry(int index) {
        return invokeMethod("historyEntry", new Object[] { index },
                            HISTORY_ENTRY_PARAMS, String.class);
    }

    public String runCommand(String command) {
        return invokeMethod("runCommand", new Object[] { command },
                            RUN_COMMAND_PARAMS, String.class);
    }

    public void close() {
        invokeMethod("close", null, null, Void.class);
    }

}
