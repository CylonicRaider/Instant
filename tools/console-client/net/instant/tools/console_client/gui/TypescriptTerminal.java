package net.instant.tools.console_client.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class TypescriptTerminal extends OverlayPane {

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
                toggleOverlayVisible();
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

    protected void fillSettingsBottomBox(Box cont) {
        cont.add(Box.createHorizontalGlue());
    }

}
