package net.instant.console;

import java.io.IOException;
import javax.management.AttributeChangeNotification;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;

public class BackendConsole implements BackendConsoleMXBean,
        NotificationEmitter {

    public static final String OUTPUT_NOTIFICATION = "instant.console.output";

    private final BackendConsoleManager parent;
    private final int id;
    private final ScriptRunner runner;
    private final CommandHistory history;
    private final CapturingWriter writer;
    private final NotificationBroadcasterSupport notifications;
    private long notificationSequence;

    protected BackendConsole(BackendConsoleManager parent, int id) {
        this.parent = parent;
        this.id = id;
        this.runner = new ScriptRunner();
        this.history = new CommandHistory();
        this.writer = new CapturingWriter();
        this.notifications = new NotificationBroadcasterSupport(
            new MBeanNotificationInfo(
                new String[] { AttributeChangeNotification.ATTRIBUTE_CHANGE },
                AttributeChangeNotification.class.getName(),
                "An attribute of this MBean has changed"
            ),
            new MBeanNotificationInfo(
                new String[] { OUTPUT_NOTIFICATION },
                String.class.getName(),
                "New text has been output on the console"
            )
        );
        this.notificationSequence = 1;
        runner.redirectOutput(writer);
        history.addListener(new CommandHistory.Listener() {
            public void historyChanged(CommandHistory history) {
                fireHistorySizeChange();
            }
        });
        writer.addListener(new CapturingWriter.Listener() {
            public void outputWritten(CapturingWriter.Event evt) {
                fireNewOutput(evt.getText());
            }
        });
    }
    public BackendConsole() {
        this(null, -1);
    }

    public BackendConsoleManager getParent() {
        return parent;
    }

    public int getID() {
        return id;
    }

    public ScriptRunner getRunner() {
        return runner;
    }

    public CommandHistory getHistory() {
        return history;
    }

    public CapturingWriter getWriter() {
        return writer;
    }

    public int getHistorySize() {
        return history.size();
    }

    public String historyEntry(int index) {
        return history.get(index);
    }

    public synchronized String runCommand(String command) {
        history.add(command);
        Object result = runner.executeSafe(command);
        String resultStr = (result == null) ? "" : result.toString();
        if (result != null) {
            try {
                writer.write(resultStr + "\n");
            } catch (IOException exc) {
                throw new RuntimeException(exc);
            }
        }
        return resultStr;
    }

    public void close() {
        if (parent != null) parent.remove(this);
    }

    public MBeanNotificationInfo[] getNotificationInfo() {
        return notifications.getNotificationInfo();
    }

    public void addNotificationListener(NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback) {
        notifications.addNotificationListener(listener, filter, handback);
    }

    public void removeNotificationListener(NotificationListener listener)
            throws ListenerNotFoundException {
        notifications.removeNotificationListener(listener);
    }

    public void removeNotificationListener(NotificationListener listener,
                                           NotificationFilter filter,
                                           Object handback)
            throws ListenerNotFoundException {
        notifications.removeNotificationListener(listener, filter, handback);
    }

    protected void fireHistorySizeChange() {
        int historySize = history.size();
        Notification n = new AttributeChangeNotification(this,
            notificationSequence++, System.currentTimeMillis(),
            "History size changed", "HistorySize", "int",
            historySize - 1, historySize);
        notifications.sendNotification(n);
    }

    protected void fireNewOutput(String text) {
        Notification n = new Notification(OUTPUT_NOTIFICATION, this,
            notificationSequence++, "New text appeared on output");
        n.setUserData(text);
        notifications.sendNotification(n);
    }

}
