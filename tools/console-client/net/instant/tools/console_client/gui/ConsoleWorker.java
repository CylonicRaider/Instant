package net.instant.tools.console_client.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.swing.SwingWorker;
import net.instant.tools.console_client.jmx.ConsoleProxy;
import net.instant.tools.console_client.jmx.Util;

// We abuse SwingWorker's functionality to shunt arbitrary Runnables (instead
// of more meaningful data) to the EDT.
public class ConsoleWorker extends SwingWorker<Void, Runnable>
        implements ActionListener, ConsoleProxy.OutputListener {

    public interface ConsoleUI {

        Typescript getTypescript();

        void setConnectionStatus(ConnectionStatus st);

        void showError(Throwable t);

    }

    public interface HistoryCallback {

        void historyReceived(String command);

    }

    public enum ConnectionStatus {
        NOT_CONNECTED("Not connected", false, false, false, 0),
        CONNECTING("Connecting...", true, false, false, 0),
        AUTH_REQUIRED("Authentication required", false, false, true, 50),
        CONNECTING_AUTH("Connecting...", true, false, true, 50),
        CONNECTED("Connected", false, true, false, 100);

        public static final int MAX_PROGRESS = 100;

        private final String description;
        private final boolean connecting;
        private final boolean connected;
        private final boolean authRequired;
        private final int progress;

        private ConnectionStatus(String description, boolean connecting,
                                 boolean connected, boolean authRequired,
                                 int progress) {
            this.description = description;
            this.connecting = connecting;
            this.connected = connected;
            this.authRequired = authRequired;
            this.progress = progress;
        }

        public String toString() {
            return description;
        }

        public boolean isConnecting() {
            return connecting;
        }

        public boolean isConnected() {
            return connected;
        }

        public boolean isAuthRequired() {
            return authRequired;
        }

        public int getProgress() {
            return progress;
        }

        public static ConnectionStatus advance(ConnectionStatus st) {
            switch (st) {
                case NOT_CONNECTED: return CONNECTING;
                case AUTH_REQUIRED: return CONNECTING_AUTH;
                default: throw new IllegalArgumentException(
                    "Cannot advance connection status " + st.name());
            }
        }

    }

    protected class HistoryNavigation implements Runnable {

        private final int shift;
        private final HistoryCallback cb;

        public HistoryNavigation(int shift, HistoryCallback cb) {
            this.shift = shift;
            this.cb = cb;
        }

        public int getShift() {
            return shift;
        }

        public HistoryCallback getCallback() {
            return cb;
        }

        public void run() {
            if (historyIndex == -1) historyIndex = console.getHistorySize();
            historyIndex += shift;
            if (historyIndex < 0) historyIndex = 0;
            final String entry = console.historyEntry(historyIndex);
            if (entry == null) historyIndex = -1;
            publish(new Runnable() {
                public void run() {
                    cb.historyReceived(entry);
                }
            });
        }

    }

    private final ConsoleUI ui;
    private final String endpoint;
    private final Map<String, Object> env;
    private final BlockingQueue<Runnable> commands;
    private JMXConnector connector;
    private ConsoleProxy console;
    private int historyIndex;
    private ConnectionStatus finalStatus;
    private Throwable finalError;

    public ConsoleWorker(ConsoleUI ui, String endpoint,
                         Map<String, Object> env) {
        this.ui = ui;
        this.endpoint = endpoint;
        this.env = Collections.unmodifiableMap(
            new HashMap<String, Object>(env));
        this.commands = new LinkedBlockingQueue<Runnable>();
        this.connector = null;
        this.console = null;
        this.historyIndex = -1;
        this.finalStatus = ConnectionStatus.NOT_CONNECTED;
        this.finalError = null;
        ui.getTypescript().addActionListener(this);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Map<String, Object> getEnv() {
        return env;
    }

    public JMXConnector getConnector() {
        return connector;
    }

    public ConsoleProxy getConsole() {
        return console;
    }

    public void historyUp(HistoryCallback cb) {
        sendCommand(new HistoryNavigation(-1, cb));
    }
    public void historyDown(HistoryCallback cb) {
        sendCommand(new HistoryNavigation(1, cb));
    }

    protected void sendCommand(Runnable r) {
        commands.add(r);
    }
    public void sendCommand(final String cmd) {
        sendCommand(new Runnable() {
            public void run() {
                console.submitCommand(cmd);
                historyIndex = -1;
            }
        });
    }

    public void close() {
        sendCommand(new Runnable() {
            public void run() {
                Thread.currentThread().interrupt();
            }
        });
    }

    protected Void doInBackground() throws IOException {
        try {
            try {
                connector = Util.connectJMX(endpoint, env);
            } catch (SecurityException exc) {
                finalStatus = ConnectionStatus.AUTH_REQUIRED;
                finalError = exc;
                return null;
            }
            MBeanServerConnection conn =
                connector.getMBeanServerConnection();
            console = ConsoleProxy.getNewDefault(conn);
            publish(new Runnable() {
                public void run() {
                    ui.setConnectionStatus(ConnectionStatus.CONNECTED);
                    ui.getTypescript().clear();
                }
            });
            console.addOutputListener(this);
            for (;;) {
                Runnable cmd = commands.take();
                cmd.run();
            }
        } catch (InterruptedException exc) {
            /* NOP -- finish normally when interrupted. */
            return null;
        } finally {
            if (console != null) console.close();
            if (connector != null) connector.close();
        }
    }

    protected void process(List<Runnable> jobs) {
        for (Runnable r : jobs) r.run();
    }

    protected void done() {
        ui.getTypescript().removeActionListener(this);
        ui.setConnectionStatus(finalStatus);
        if (finalError != null) ui.showError(finalError);
        try {
            get();
        } catch (InterruptedException exc) {
            // The get() itself *should* finish immediately.
            throw new RuntimeException(exc);
        } catch (ExecutionException exc) {
            // Ensure potential IOException-s get reported somewhere.
            throw new RuntimeException(exc);
        }
    }

    public void actionPerformed(ActionEvent evt) {
        Typescript ts = ui.getTypescript();
        if (evt.getSource() == ts.getInput()) {
            String cmd = ts.getInputText(true);
            ts.appendOutput(ts.getPromptText() + cmd + "\n");
            sendCommand(cmd);
        }
    }

    public void outputReceived(ConsoleProxy.OutputEvent evt) {
        final String data = evt.getData();
        if (data == null) {
            close();
            return;
        }
        publish(new Runnable() {
            public void run() {
                ui.getTypescript().appendOutput(data);
            }
        });
    }

}
