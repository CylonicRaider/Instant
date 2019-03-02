package net.instant.tools.console_client.jmx;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public class ConsoleProxy extends JMXObjectProxy {

    private static final String[] P_HISTORY_ENTRY = { int.class.getName() };
    private static final String[] P_RUN_COMMAND = { String.class.getName() };
    private static final String[] P_SUBMIT_COMMAND = P_RUN_COMMAND;

    private final AtomicBoolean shutdownHookInstalled;
    private Integer id;
    private boolean closed;

    public ConsoleProxy(MBeanServerConnection connection,
                        ObjectName objectName) {
        super(connection, objectName);
        shutdownHookInstalled = new AtomicBoolean(false);
        closed = false;
    }

    public int getID() {
        if (id == null) id = invokeMethod("getID", null, null, Integer.class);
        return id;
    }

    public int historySize() {
        return invokeMethod("historySize", null, null, Integer.class);
    }

    public String historyEntry(int index) {
        return invokeMethod("historyEntry", new Object[] { index },
                            P_HISTORY_ENTRY, String.class);
    }

    public String runCommand(String command) {
        return invokeMethod("runCommand", new Object[] { command },
                            P_RUN_COMMAND, String.class);
    }

    public long submitCommand(String command) {
        return invokeMethod("submitCommand", new Object[] { command },
                            P_SUBMIT_COMMAND, Long.class);
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
