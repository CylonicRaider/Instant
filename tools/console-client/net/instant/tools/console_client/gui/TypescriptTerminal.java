package net.instant.tools.console_client.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;

public class TypescriptTerminal extends JLayeredPane {

    public static class OverlayPanel extends JPanel implements MouseListener {

        public static final Color DEFAULT_BACKGROUND = new Color(0x80000000,
                                                                 true);

        public OverlayPanel() {
            setBackground(DEFAULT_BACKGROUND);
            setOpaque(false);
            addMouseListener(this);
        }

        public void paintComponent(Graphics g) {
            // If isOpaque() is true, the painting system does not render
            // anything behind us; if isOpaque() is false, JPanel does not
            // render its background. The latter being more easy to correct,
            // we do it here.
            if (! isOpaque()) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            super.paintComponent(g);
        }

        public void mouseEntered(MouseEvent evt) {}
        public void mousePressed(MouseEvent evt) {}
        public void mouseReleased(MouseEvent evt) {}
        public void mouseClicked(MouseEvent evt) {}
        public void mouseExited(MouseEvent evt) {}

    }

    // HACK: Swing's default LAF uses the LayoutFocusTraversalPolicy; we
    //       assume that the actually used LAF will do so, too. An arguably
    //       better approach would be to locate the FTP responsible for the
    //       component and proxying to it, but this is easier.
    protected class OverlayFocusTraversalPolicy
            extends LayoutFocusTraversalPolicy {

        private String shortString(Object obj) {
            return obj.getClass().getName() + '@' +
                Integer.toHexString(obj.hashCode());
        }

        protected boolean accept(Component comp) {
            if (! super.accept(comp))
                return false;
            if (morePane.isVisible() &&
                    SwingUtilities.isDescendingFrom(comp, ts))
                return false;
            return true;
        }

    }

    protected class SettingsDialog extends JPanel implements ActionListener {

        protected final JLabel heading;
        protected final Box bottom;
        protected final JButton ok;

        public SettingsDialog() {
            heading = new JLabel("Settings");
            bottom = Box.createHorizontalBox();
            ok = new JButton("OK");
            createUI();
        }

        protected void createUI() {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.fill = GridBagConstraints.BOTH;
            c.weightx = 1;
            setLayout(new GridBagLayout());

            heading.setHorizontalAlignment(JLabel.CENTER);
            Font hf = heading.getFont().deriveFont(Font.PLAIN);
            heading.setFont(hf.deriveFont(hf.getSize() * 1.5f));
            add(heading, c);

            add(EnumSelector.createFor(getTypescript().createLWPModel()), c);

            fillSettingsBottomBox(bottom);

            ok.addActionListener(this);
            bottom.add(ok);

            add(bottom, c);
        }

        public void actionPerformed(ActionEvent evt) {
            if (evt.getSource() == more || evt.getSource() == ok) {
                showSettingsPopup(! morePane.isVisible());
            }
        }

    }

    private static final Color SETTINGS_BORDER = Color.BLACK;
    private static final int SETTINGS_PADDING = 2;

    private final Typescript ts;
    private final JButton more;
    private final JPanel morePane;
    private final SettingsDialog settings;

    public TypescriptTerminal() {
        ts = new Typescript();
        more = new JButton("More...");
        morePane = new OverlayPanel();
        settings = createSettingsDialog();
        createUI();
    }

    protected SettingsDialog createSettingsDialog() {
        return new SettingsDialog();
    }

    protected void createUI() {
        setFocusTraversalPolicyProvider(true);
        setFocusTraversalPolicy(new OverlayFocusTraversalPolicy());
        setLayout(new OverlayLayout(this));

        more.addActionListener(settings);
        ts.getBottomPane().add(more, BorderLayout.EAST);

        add(ts, DEFAULT_LAYER);

        morePane.setVisible(false);
        morePane.setLayout(new GridBagLayout());

        settings.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SETTINGS_BORDER),
            BorderFactory.createEmptyBorder(SETTINGS_PADDING,
                SETTINGS_PADDING, SETTINGS_PADDING, SETTINGS_PADDING)
        ));
        morePane.add(settings);

        add(morePane, MODAL_LAYER);
    }

    public Typescript getTypescript() {
        return ts;
    }

    protected SettingsDialog getSettings() {
        return settings;
    }

    protected void fillSettingsBottomBox(Box cont) {
        cont.add(Box.createHorizontalGlue());
    }

    protected void showSettingsPopup(boolean visible) {
        morePane.setVisible(visible);
        KeyboardFocusManager mgr =
            KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Component focusOwner = mgr.getFocusOwner();
        Container forbiddenParent, replacementParent;
        if (visible) {
            forbiddenParent = ts;
            replacementParent = morePane;
        } else {
            forbiddenParent = morePane;
            replacementParent = ts;
        }
        if (SwingUtilities.isDescendingFrom(focusOwner, forbiddenParent)) {
            Component newOwner = getFocusTraversalPolicy()
                .getDefaultComponent(replacementParent);
            newOwner.requestFocusInWindow();
        }
    }

}
