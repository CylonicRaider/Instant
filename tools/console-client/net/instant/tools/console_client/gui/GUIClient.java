package net.instant.tools.console_client.gui;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import net.instant.tools.console_client.jmx.Util;

public class GUIClient extends TypescriptTerminal {

    public enum ConnectionStatus {
        NOT_CONNECTED("Not connected", false, false, 0),
        CONNECTING("Connecting...", true, false, 0),
        AUTH_REQUIRED("Authentication required", false, false, 50),
        CONNECTING_AUTH("Connecting...", true, false, 50),
        CONNECTED("Connected", false, true, 100);

        public static final int MAX_PROGRESS = 100;

        private final String description;
        private final boolean connecting;
        private final boolean connected;
        private final int progress;

        private ConnectionStatus(String description, boolean connecting,
                                 boolean connected, int progress) {
            this.description = description;
            this.connecting = connecting;
            this.connected = connected;
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

        public int getProgress() {
            return progress;
        }

    }

    public class ConnectionPopup extends OverlayDialog
            implements ActionListener {

        // Enough to hold "localhost:65535".
        public static final int ENDPOINT_COLUMNS = 15;

        protected final JTextField endpointField;
        protected final JTextField usernameField;
        protected final JPasswordField passwordField;
        protected final JProgressBar progressBar;
        protected final JLabel statusLabel;
        protected final JButton connectButton;

        public ConnectionPopup() {
            super("Connection");
            endpointField = new JTextField();
            usernameField = new JTextField();
            passwordField = new JPasswordField();
            progressBar = new JProgressBar(0, ConnectionStatus.MAX_PROGRESS);
            statusLabel = new JLabel();
            connectButton = new JButton("Connect");
            createUI();
        }

        protected void createUI() {
            Container cnt = getContentPane();
            cnt.setLayout(new GridBagLayout());

            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.fill = GridBagConstraints.BOTH;
            cnt.add(new JLabel("Connect to: "), c);
            cnt.add(new JLabel("Username: "), c);
            cnt.add(new JLabel("Password: "), c);

            c.gridx = 1;
            c.weightx = 1;
            endpointField.setColumns(ENDPOINT_COLUMNS);
            cnt.add(endpointField, c);
            cnt.add(usernameField, c);
            cnt.add(passwordField, c);

            c.gridx = 0;
            c.weighty = 1;
            cnt.add(Box.createGlue(), c);

            c.gridwidth = 2;
            cnt.add(progressBar, c);

            Container bottom = getBottomPane();
            bottom.add(statusLabel);
            bottom.add(Box.createHorizontalGlue());
            connectButton.addActionListener(this);
            bottom.add(connectButton);
        }

        public void actionPerformed(ActionEvent evt) {
            if (evt.getSource() == connectButton) {
                showPopup(null);
            }
        }

        public void setStatus(ConnectionStatus st) {
            if (st.isConnecting()) {
                progressBar.setIndeterminate(true);
            } else {
                progressBar.setValue(st.getProgress());
                progressBar.setIndeterminate(false);
            }
            statusLabel.setText(st.toString());
        }

        public String getEndpoint() {
            return endpointField.getText();
        }
        public void setEndpoint(String ep) {
            endpointField.setText(ep);
        }

        public void exportCredentials(Map<String, Object> env) {
            // The password is stored as a string since the JMX authenticator
            // expects an array of strings anyway.
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            Util.insertCredentials(env, username, password);
        }
        public void initCredentials(String username, String password) {
            if (username != null) usernameField.setText(username);
            if (password != null) passwordField.setText(password);
        }

    }

    private final ConnectionPopup connection;

    public GUIClient() {
        connection = new ConnectionPopup();
        createUI();
    }

    protected void createUI() {
        showPopup(connection);
    }

}
