package net.instant.tools.console_client.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;

public class TypescriptTerminal extends OverlayPane {

    protected class SettingsDialog extends OverlayDialog
            implements ActionListener, ItemListener {

        protected final JCheckBox outputFocusable;
        protected final JButton ok;

        public SettingsDialog() {
            super("Settings");
            outputFocusable = new JCheckBox("Output selectable");
            ok = new JButton("OK");
            createUI();
        }

        protected void createUI() {
            Container content = getContentPane();
            content.add(EnumSelector.createFor(
                getTypescript().createLWPModel()));

            outputFocusable.setToolTipText("Workaround around a minor tab " +
                "order invonvenience.");
            outputFocusable.setSelected(ts.isOutputFocusable());
            outputFocusable.addItemListener(this);
            content.add(outputFocusable, BorderLayout.SOUTH);

            Container bottom = getBottomPane();
            fillSettingsBottomBox(bottom);
            ok.setMnemonic(KeyEvent.VK_O);
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

        public void itemStateChanged(ItemEvent evt) {
            if (evt.getSource() == outputFocusable) {
                ts.setOutputFocusable(outputFocusable.isSelected());
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
        initTypescriptTerminal();
    }

    protected SettingsDialog createSettingsDialog() {
        return new SettingsDialog();
    }

    protected void fillSettingsBottomBox(Container cont) {
        cont.add(Box.createHorizontalGlue());
    }

    protected void initTypescriptTerminal() {
        more.setMnemonic(KeyEvent.VK_M);
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
