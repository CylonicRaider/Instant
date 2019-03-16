package net.instant.tools.console_client.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.util.HashMap;
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

public class GUIClient extends TypescriptTerminal
        implements ActionListener, ConsoleWorker.ConsoleUI {

    public class ConnectionPopup extends OverlayDialog
            implements ActionListener, DocumentListener {

        // Enough to hold "localhost:65535".
        public static final int ENDPOINT_COLUMNS = 15;

        protected final JTextField endpointField;
        protected final JTextField usernameField;
        protected final JPasswordField passwordField;
        protected final JProgressBar progressBar;
        protected final JLabel statusLabel;
        protected final Box dockingArea;
        protected final JButton connectButton;
        private final JTextComponent[] fields;
        private ConsoleWorker.ConnectionStatus status;

        public ConnectionPopup(Map<String, String> arguments) {
            super("Connection");
            endpointField = new JTextField();
            usernameField = new JTextField();
            passwordField = new JPasswordField();
            progressBar = new JProgressBar(0,
                ConsoleWorker.ConnectionStatus.MAX_PROGRESS);
            statusLabel = new JLabel();
            dockingArea = Box.createHorizontalBox();
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
            cnt.add(createLabelFor("Connect to: ", KeyEvent.VK_T,
                                   endpointField), c);
            cnt.add(createLabelFor("Username: ", KeyEvent.VK_U,
                                   usernameField), c);
            cnt.add(createLabelFor("Password: ", KeyEvent.VK_P,
                                   passwordField), c);

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
            bottom.add(dockingArea);
            connectButton.setMnemonic(KeyEvent.VK_C);
            connectButton.setEnabled(false);
            connectButton.addActionListener(this);
            bottom.add(connectButton);
        }

        public ConsoleWorker.ConnectionStatus getStatus() {
            return status;
        }
        public void setStatus(ConsoleWorker.ConnectionStatus st) {
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

        public void showError(Throwable t) {
            if (t instanceof SecurityException) {
                statusLabel.setText(t.getMessage());
            } else {
                statusLabel.setText(t.toString());
            }
        }

        public Box getDockingArea() {
            return dockingArea;
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

        public void selectNextEmptyField() {
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
            if (! nonempty(usernameField) || ! nonempty(passwordField))
                return;
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
    // Must be non-final to be set in a non-constructor method.
    private JButton close;
    private ConsoleWorker worker;

    public GUIClient(Map<String, String> arguments) {
        connection = new ConnectionPopup(arguments);
        worker = null;
        createUI();
    }
    public GUIClient() {
        this(null);
    }

    protected void fillSettingsBottomBox(Container cont) {
        super.fillSettingsBottomBox(cont);
        close = new JButton("Close");
        close.setMnemonic(KeyEvent.VK_C);
        close.addActionListener(this);
        cont.add(close);
    }

    protected void createUI() {
        showPopup(connection);
        connection.setStatus(ConsoleWorker.ConnectionStatus.NOT_CONNECTED);
    }

    public ConnectionPopup getConnectionPopup() {
        return connection;
    }

    public void actionPerformed(ActionEvent evt) {
        if (evt.getSource() == close) {
            if (worker != null) worker.close();
        }
    }

    public ConsoleWorker.ConnectionStatus getConnectionStatus() {
        return connection.getStatus();
    }
    public void setConnectionStatus(ConsoleWorker.ConnectionStatus st) {
        connection.setStatus(st);
        switch (st) {
            case NOT_CONNECTED:
                showPopup(connection);
                break;
            case CONNECTED:
                showPopup(null);
                break;
        }
    }

    public void showError(Throwable t) {
        connection.showError(t);
    }

    protected ConsoleWorker createWorker(String endpoint,
                                         Map<String, Object> env) {
        return new ConsoleWorker(this, endpoint, env);
    }

    public boolean doConnect() {
        if (! connection.isComplete()) return false;
        String endpoint = connection.getEndpoint();
        Map<String, Object> env = new HashMap<String, Object>();
        connection.exportCredentials(env);
        connection.setStatus(ConsoleWorker.ConnectionStatus.advance(
            connection.getStatus()));
        worker = createWorker(endpoint, env);
        worker.execute();
        return true;
    }

    private static boolean nonempty(JTextComponent comp) {
        return comp.getDocument().getLength() != 0;
    }

    public static JLabel createLabelFor(String text, int mnemonic,
                                        Component target) {
        JLabel ret = new JLabel(text);
        ret.setDisplayedMnemonic(mnemonic);
        ret.setLabelFor(target);
        return ret;
    }

    public static void showDefault(final Map<String, String> arguments) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                final JFrame win = new JFrame(WINDOW_TITLE);
                win.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                JButton quit = new JButton("Quit");
                quit.setMnemonic(KeyEvent.VK_Q);
                quit.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent evt) {
                        // Simulate a proper window close.
                        win.dispatchEvent(new WindowEvent(win,
                            WindowEvent.WINDOW_CLOSING));
                    }
                });
                GUIClient client = new GUIClient(arguments);
                client.getTypescript().setDisplaySize(80, 25);
                client.getTypescript().setPromptText("> ");
                client.getConnectionPopup().getDockingArea().add(quit);
                client.doConnect();
                win.add(client);
                win.pack();
                client.getConnectionPopup().selectNextEmptyField();
                win.setLocationRelativeTo(null);
                win.setVisible(true);
            }
        });
    }

}
