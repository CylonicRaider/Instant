package net.instant.tools.console_client.gui;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import net.instant.tools.console_client.jmx.Util;

public class GUIClient extends TypescriptTerminal {

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

    }

    public class ConnectionPopup extends OverlayDialog
            implements ActionListener, DocumentListener {

        // Enough to hold "localhost:65535".
        public static final int ENDPOINT_COLUMNS = 15;

        protected final JTextField endpointField;
        protected final JTextField usernameField;
        protected final JPasswordField passwordField;
        protected final JProgressBar progressBar;
        protected final JLabel statusLabel;
        protected final JButton connectButton;
        private final JTextComponent[] fields;
        private ConnectionStatus status;

        public ConnectionPopup(Map<String, String> arguments) {
            super("Connection");
            endpointField = new JTextField();
            usernameField = new JTextField();
            passwordField = new JPasswordField();
            progressBar = new JProgressBar(0, ConnectionStatus.MAX_PROGRESS);
            statusLabel = new JLabel();
            connectButton = new JButton("Connect");
            fields = new JTextComponent[] { endpointField, usernameField,
                                            passwordField };
            status = null;
            if (arguments != null) {
                endpointField.setText(arguments.get("address"));
                usernameField.setText(arguments.get("login"));
            }
            createUI();
        }
        public ConnectionPopup() {
            this(null);
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
            endpointField.addActionListener(this);
            usernameField.addActionListener(this);
            passwordField.addActionListener(this);
            endpointField.getDocument().addDocumentListener(this);
            usernameField.getDocument().addDocumentListener(this);
            passwordField.getDocument().addDocumentListener(this);
            cnt.add(endpointField, c);
            cnt.add(usernameField, c);
            cnt.add(passwordField, c);

            c.gridx = 0;
            c.weightx = 0;
            c.weighty = 1;
            cnt.add(Box.createGlue(), c);

            c.weighty = 0;
            c.gridwidth = 2;
            cnt.add(progressBar, c);

            Container bottom = getBottomPane();
            bottom.add(statusLabel);
            bottom.add(Box.createHorizontalGlue());
            connectButton.setEnabled(false);
            connectButton.addActionListener(this);
            bottom.add(connectButton);
        }

        public ConnectionStatus getStatus() {
            return status;
        }
        public void setStatus(ConnectionStatus st) {
            status = st;
            if (st.isConnecting()) {
                progressBar.setIndeterminate(true);
            } else {
                progressBar.setValue(st.getProgress());
                progressBar.setIndeterminate(false);
            }
            statusLabel.setText(st.toString());
            updateButton();
            if (! st.isConnecting()) selectNextEmptyField();
        }

        public void actionPerformed(ActionEvent evt) {
            Object src = evt.getSource();
            if (src == connectButton) {
                doConnect();
            } else if (src == endpointField || src == usernameField ||
                       src == passwordField) {
                if (isComplete()) {
                    if (connectButton.isEnabled()) doConnect();
                } else {
                    selectNextEmptyField();
                }
            }
        }

        public void changedUpdate(DocumentEvent evt) {
            updateButton();
        }
        public void insertUpdate(DocumentEvent evt) {
            updateButton();
        }
        public void removeUpdate(DocumentEvent evt) {
            updateButton();
        }

        private void updateButton() {
            connectButton.setEnabled(isComplete() && status != null &&
                ! status.isConnecting() && ! status.isConnected());
        }

        protected void selectNextEmptyField() {
            for (JTextComponent field : fields) {
                if (nonempty(field)) continue;
                field.requestFocusInWindow();
                break;
            }
        }

        public boolean isComplete() {
            // FIXME: This does not allow genuinely empty usernames or
            //        passwords. Blame me when you actually get hit by this
            //        one.
            return (nonempty(endpointField) &&
                    nonempty(usernameField) == nonempty(passwordField) &&
                    (status == null || ! status.isAuthRequired() ||
                     nonempty(usernameField)));
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

    public static final String WINDOW_TITLE = "Instant backend console";

    private final ConnectionPopup connection;

    public GUIClient(Map<String, String> arguments) {
        connection = new ConnectionPopup(arguments);
        createUI();
    }
    public GUIClient() {
        this(null);
    }

    protected void createUI() {
        showPopup(connection);
        connection.setStatus(ConnectionStatus.NOT_CONNECTED);
        if (connection.isComplete()) doConnect();
    }

    public ConnectionPopup getConnectionPopup() {
        return connection;
    }

    private void doConnect() {
        // TODO: Do connect.
        showPopup(null);
    }

    private static boolean nonempty(JTextComponent comp) {
        return comp.getDocument().getLength() != 0;
    }

    public static void showDefault(final Map<String, String> arguments) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GUIClient client = new GUIClient(arguments);
                client.getTypescript().setDisplaySize(80, 25);
                JFrame win = new JFrame(WINDOW_TITLE);
                win.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                win.add(client);
                win.pack();
                client.getConnectionPopup().selectNextEmptyField();
                win.setLocationRelativeTo(null);
                win.setVisible(true);
            }
        });
    }

}
