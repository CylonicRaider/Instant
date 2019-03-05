package net.instant.tools.console_client.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

public class StreamPairTerminal implements Terminal {

    private final BufferedReader reader;
    private final Writer writer;
    private final Writer prompts;

    public StreamPairTerminal(Reader reader, Writer writer, Writer prompts) {
        this.reader = (reader instanceof BufferedReader) ?
            (BufferedReader) reader : new BufferedReader(reader);
        this.writer = writer;
        this.prompts = prompts;
    }

    public BufferedReader getReader() {
        return reader;
    }

    public Writer getWriter() {
        return writer;
    }

    public Writer getPromptWriter() {
        return prompts;
    }

    public String readLine(String prompt) {
        showPrompt(prompt);
        try {
            return reader.readLine();
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
    }

    public String readPassword(String prompt) {
        return readLine(prompt);
    }

    public void write(String text) {
        writeAndFlush(writer, text);
    }

    protected void showPrompt(String text) {
        if (prompts == null) return;
        writeAndFlush(prompts, text);
    }

    protected static void writeAndFlush(Writer w, String text) {
        try {
            w.write(text);
            w.flush();
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
    }

    public static StreamPairTerminal getDefault(boolean showPrompts) {
        Writer pw = (showPrompts) ? new OutputStreamWriter(System.err) : null;
        return new StreamPairTerminal(new InputStreamReader(System.in),
                                      new OutputStreamWriter(System.out), pw);
    }

}
