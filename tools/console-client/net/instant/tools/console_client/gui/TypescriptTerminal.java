package net.instant.tools.console_client.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Box;
import javax.swing.JButton;

public class TypescriptTerminal extends OverlayPane {

    protected class SettingsDialog extends OverlayDialog
            implements ActionListener {

        protected final JButton ok;

        public SettingsDialog() {
            super("Settings");
            ok = new JButton("OK");
            createUI();
        }

        protected void createUI() {
            getContentPane().add(EnumSelector.createFor(
                getTypescript().createLWPModel()));

            Container bottom = getBottomPane();
            fillSettingsBottomBox(bottom);
            ok.addActionListener(this);
            bottom.add(ok);
        }

        public void actionPerformed(ActionEvent evt) {
            if (evt.getSource() == more) {
                showPopup(settings);
            } else if (evt.getSource() == ok) {
                showPopup(null);
            }
        }

    }

    private final Typescript ts;
    private final JButton more;
    private final SettingsDialog settings;

    public TypescriptTerminal() {
        ts = new Typescript();
        more = new JButton("More...");
        settings = createSettingsDialog();
        createUI();
    }

    protected SettingsDialog createSettingsDialog() {
        return new SettingsDialog();
    }

    protected void fillSettingsBottomBox(Container cont) {
        cont.add(Box.createHorizontalGlue());
    }

    protected void createUI() {
        more.addActionListener(settings);
        ts.getBottomPane().add(more, BorderLayout.EAST);
        setContent(ts);
        setOverlay(settings);
    }

    public Typescript getTypescript() {
        return ts;
    }

    protected SettingsDialog getSettings() {
        return settings;
    }

    public void showPopup(Component content) {
        if (content == null) {
            setOverlayVisible(false);
        } else {
            setOverlay(content);
            setOverlayVisible(true);
        }
    }

}
