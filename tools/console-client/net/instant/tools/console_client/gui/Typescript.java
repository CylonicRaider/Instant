package net.instant.tools.console_client.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/* The name is taken from com.sun.tools.example.debug.gui.TypeScript. */
public class Typescript extends JPanel {

    public enum LineWrapPolicy {
        NONE(false, false),
        WORDS(true, true),
        CHARS(true, false);

        private final boolean lineWrap;
        private final boolean wrapStyleWord;

        private LineWrapPolicy(boolean lineWrap, boolean wrapStyleWord) {
            this.lineWrap = lineWrap;
            this.wrapStyleWord = wrapStyleWord;
        }

        public boolean isWrappingLines() {
            return lineWrap;
        }

        public boolean isWrappingAtWords() {
            return wrapStyleWord;
        }

        public void apply(JTextArea area) {
            area.setLineWrap(lineWrap);
            area.setWrapStyleWord(wrapStyleWord);
        }

        public static LineWrapPolicy determine(JTextArea area) {
            if (! area.getLineWrap())
                return NONE;
            return (area.getWrapStyleWord()) ? WORDS : CHARS;
        }

    }

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
        output.setEditable(false);
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

    public JPanel getBottomPane() {
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

    public Dimension getDisplaySize() {
        return new Dimension(output.getColumns(), output.getRows());
    }
    public void setDisplaySize(Dimension dim) {
        output.setColumns(dim.width);
        output.setRows(dim.height);
    }

    public LineWrapPolicy getLineWrapPolicy() {
        return LineWrapPolicy.determine(output);
    }
    public void setLineWrapPolicy(LineWrapPolicy p) {
        p.apply(output);
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

    public String getPromptText() {
        return prompt.getText();
    }
    public void setPromptText(String p) {
        prompt.setText(p);
    }

    public String getInputText(boolean clear) {
        String ret = input.getText();
        if (clear) input.setText("");
        return ret;
    }

}
