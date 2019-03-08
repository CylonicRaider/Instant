package net.instant.tools.console_client.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/* The name is taken from com.sun.tools.example.debug.gui.TypeScript. */
public class Typescript extends JPanel {

    private final JScrollPane scroller;
    private final JTextArea output;
    private final JPanel bottom;
    private final JLabel prompt;
    private final JTextField input;

    public Typescript() {
        scroller = new JScrollPane();
        output = new JTextArea();
        bottom = new JPanel();
        prompt = new JLabel();
        input = new JTextField();
        createUI();
    }

    protected void createUI() {
        setLayout(new BorderLayout());
        scroller.setViewportView(output);
        add(scroller);
        bottom.setLayout(new BorderLayout());
        bottom.add(prompt, BorderLayout.WEST);
        bottom.add(input);
        add(bottom, BorderLayout.SOUTH);
        setDefaultDisplayFont();
    }

    public JScrollPane getScroller() {
        return scroller;
    }

    public JTextArea getOutput() {
        return output;
    }

    public JPanel getBottom() {
        return bottom;
    }

    public JLabel getPrompt() {
        return prompt;
    }

    public JTextField getInput() {
        return input;
    }

    public void setDefaultDisplayFont() {
        Font base = output.getFont();
        setDisplayFont(new Font(Font.MONOSPACED, Font.PLAIN, base.getSize()));
    }

    public void setDisplayFont(Font f) {
        output.setFont(f);
        prompt.setFont(f);
        input.setFont(f);
    }

    public void appendOutput(String text) {
        output.append(text);
        output.setCaretPosition(output.getText().length());
    }

    public void addActionListener(ActionListener l) {
        input.addActionListener(l);
    }

    public void removeActionListener(ActionListener l) {
        input.removeActionListener(l);
    }

    public String getInput(boolean clear) {
        String ret = input.getText();
        if (clear) input.setText("");
        return ret;
    }

}
