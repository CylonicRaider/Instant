package net.instant.tools.console_client.jmx;

import java.io.Closeable;
import java.util.EventListener;
import java.util.EventObject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

public class ConsoleProxy extends JMXObjectProxy implements Closeable {

    public class OutputEvent extends EventObject {

        private final long sequence;
        private final String data;
        private final Object cause;

        public OutputEvent(Object source, long sequence, String data,
                           Object cause) {
            super(source);
            this.sequence = sequence;
            this.data = data;
            this.cause = cause;
        }

        public long getSequence() {
            return sequence;
        }

        public String getData() {
            return data;
        }

        public Object getCause() {
            return cause;
        }

    }

    public interface OutputListener extends EventListener {

        void outputReceived(OutputEvent evt);

    }

    protected class JMXListener implements NotificationListener {

        public void handleNotification(Notification n, Object handback) {
            String dataStr = String.valueOf(n.getUserData());
            fireOutputEvent(new OutputEvent(ConsoleProxy.this,
                n.getSequenceNumber(), dataStr, n));
        }

    }

    // The well-known type of the notification for console output.
    public static final String OUTPUT_NOTIFICATION = "instant.console.output";

    private static final String[] P_HISTORY_ENTRY = { int.class.getName() };
    private static final String[] P_RUN_COMMAND = { String.class.getName() };
    private static final String[] P_SUBMIT_COMMAND = P_RUN_COMMAND;

    private final List<OutputListener> listeners;
    private final AtomicBoolean shutdownHookInstalled;
    private Integer id;
    private boolean closed;

    public ConsoleProxy(MBeanServerConnection connection,
                        ObjectName objectName) {
        super(connection, objectName);
        listeners = new CopyOnWriteArrayList<OutputListener>();
        shutdownHookInstalled = new AtomicBoolean(false);
        closed = false;
        listenNotificationType(OUTPUT_NOTIFICATION, new JMXListener(), null);
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

    public void addOutputListener(OutputListener l) {
        listeners.add(l);
    }

    public void removeOutputListener(OutputListener l) {
        listeners.remove(l);
    }

    protected void fireOutputEvent(OutputEvent evt) {
        for (OutputListener l : listeners) {
            l.outputReceived(evt);
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
