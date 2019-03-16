package net.instant.tools.console_client.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.swing.SwingWorker;
import net.instant.tools.console_client.jmx.ConsoleProxy;
import net.instant.tools.console_client.jmx.Util;

// We abuse the interface to leverage SwingWorker's transmission of values
// (which we immediately execute) to the EDT.
public class ConsoleWorker extends SwingWorker<Void, Runnable>
        implements ActionListener {

    public interface ConsoleUI {

        Typescript getTypescript();

        void setConnectionStatus(ConnectionStatus st);

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

    private class StatusSetter implements Runnable {

        private final ConnectionStatus status;

        public StatusSetter(ConnectionStatus status) {
            this.status = status;
        }

        public void run() {
            ui.setConnectionStatus(status);
        }

    }

    private class OutputDisplayer implements Runnable {

        private final String chunk;

        public OutputDisplayer(String chunk) {
            this.chunk = chunk;
        }

        public void run() {
            ui.getTypescript().appendOutput(chunk);
        }

    }

    // Apparently, LinkedBlockingQueue does not like null values, so we
    // put them into a trivial wrapper class.
    private class Command {

        private final String content;

        public Command(String content) {
            this.content = content;
        }

        public String toString() {
            return String.valueOf(content);
        }

        public String getContent() {
            return content;
        }

    }

    private class OutputListener implements ConsoleProxy.OutputListener {

        public void outputReceived(ConsoleProxy.OutputEvent evt) {
            if (evt.getData() == null) {
                sendCommand(null);
            } else {
                publish(new OutputDisplayer(evt.getData()));
            }
        }

    }

    private final ConsoleUI ui;
    private final String endpoint;
    private final Map<String, Object> env;
    private final BlockingQueue<Command> commands;
    private JMXConnector connector;
    private ConsoleProxy console;
    private ConnectionStatus finalStatus;

    public ConsoleWorker(ConsoleUI ui, String endpoint,
                         Map<String, Object> env) {
        this.ui = ui;
        this.endpoint = endpoint;
        this.env = Collections.unmodifiableMap(
            new HashMap<String, Object>(env));
        this.commands = new LinkedBlockingQueue<Command>();
        this.connector = null;
        this.console = null;
        this.finalStatus = ConnectionStatus.NOT_CONNECTED;
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

    public void sendCommand(String cmd) {
        commands.add(new Command(cmd));
    }

    protected Void doInBackground()
            throws IOException, InterruptedException {
        try {
            try {
                connector = Util.connectJMX(endpoint, env);
            } catch (SecurityException exc) {
                System.err.println(
                    "WARNING: Exception while connecting: " + exc);
                finalStatus = ConnectionStatus.AUTH_REQUIRED;
                return null;
            }
            MBeanServerConnection conn =
                connector.getMBeanServerConnection();
            console = ConsoleProxy.getNewDefault(conn);
            publish(new StatusSetter(ConnectionStatus.CONNECTED));
            ui.getTypescript().clear();
            console.addOutputListener(new OutputListener());
            for (;;) {
                Command cmd = commands.take();
                if (cmd.getContent() == null) break;
                console.submitCommand(cmd.getContent());
            }
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
        ui.setConnectionStatus(finalStatus);
        ui.getTypescript().removeActionListener(this);
    }

    public void actionPerformed(ActionEvent evt) {
        Typescript ts = ui.getTypescript();
        if (evt.getSource() == ts.getInput()) {
            String cmd = ts.getInputText(true);
            ts.appendOutput(ts.getPromptText() + cmd + "\n");
            sendCommand(cmd);
        }
    }

}
