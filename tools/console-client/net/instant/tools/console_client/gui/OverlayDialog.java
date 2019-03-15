package net.instant.tools.console_client.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;

public class OverlayDialog extends JPanel {

    public static final Border BORDER =
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.BLACK),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        );

    private JLabel titleBar;
    private Container contentPane;
    private Container bottomPane;

    public OverlayDialog(String title) {
        titleBar = new JLabel(title);
        contentPane = new JPanel();
        bottomPane = new JPanel();
        initOverlayDialog();
    }
    public OverlayDialog() {
        this(null);
    }

    protected void initOverlayDialog() {
        setBorder(BORDER);
        setLayout(new BorderLayout());

        Font hf = titleBar.getFont().deriveFont(Font.BOLD);
        titleBar.setFont(hf.deriveFont(hf.getSize() * 1.5f));
        titleBar.setHorizontalAlignment(JLabel.CENTER);
        add(titleBar, BorderLayout.NORTH);

        contentPane.setLayout(new BorderLayout());
        add(contentPane);

        bottomPane.setLayout(new BoxLayout(bottomPane, BoxLayout.X_AXIS));
        add(bottomPane, BorderLayout.SOUTH);
    }

    public JLabel getTitleBar() {
        return titleBar;
    }
    public void setTitleBar(JLabel bar) {
        if (bar == titleBar) return;
        if (titleBar != null) remove(titleBar);
        titleBar = bar;
        if (titleBar != null) add(titleBar, BorderLayout.NORTH);
    }

    public Container getContentPane() {
        return contentPane;
    }
    public void setContentPane(Container pane) {
        if (pane == contentPane) return;
        if (contentPane != null) remove(contentPane);
        contentPane = pane;
        if (contentPane != null) add(contentPane);
    }

    public Container getBottomPane() {
        return bottomPane;
    }
    public void setBottomPane(Container pane) {
        if (pane == bottomPane) return;
        if (bottomPane != null) remove(bottomPane);
        bottomPane = pane;
        if (bottomPane != null) add(bottomPane, BorderLayout.SOUTH);
    }

    public String getTitle() {
        return titleBar.getText();
    }
    public void setTitle(String title) {
        titleBar.setText(title);
    }

}
