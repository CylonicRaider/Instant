package net.instant.tools.console_client.jmx;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public class ConsoleProxy extends JMXObjectProxy {

    private final String[] HISTORY_ENTRY_PARAMS = { int.class.getName() };
    private final String[] RUN_COMMAND_PARAMS = { String.class.getName() };

    private final AtomicBoolean shutdownHookInstalled;
    private boolean closed;

    public ConsoleProxy(MBeanServerConnection connection,
                        ObjectName objectName) {
        super(connection, objectName);
        shutdownHookInstalled = new AtomicBoolean(false);
        closed = false;
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
        synchronized (this) {
            if (closed) return;
            invokeMethod("close", null, null, Void.class);
            closed = true;
        }
    }

    public void installShutdownHook() {
        if (! shutdownHookInstalled.compareAndSet(false, true)) return;
        Runtime.getRuntime().addShutdownHook(
            new Thread("ConsoleProxy closer") {
                public void run() {
                    close();
                }
            }
        );
    }

    public static ConsoleProxy getNewDefault(MBeanServerConnection conn) {
        ConsoleManagerProxy mgr = ConsoleManagerProxy.getDefault(conn);
        ConsoleProxy con = mgr.newConsole();
        con.installShutdownHook();
        return con;
    }

}
