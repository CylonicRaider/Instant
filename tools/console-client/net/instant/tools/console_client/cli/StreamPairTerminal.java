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

    public StreamPairTerminal(Reader reader, Writer writer) {
        this.reader = (reader instanceof BufferedReader) ?
            (BufferedReader) reader : new BufferedReader(reader);
        this.writer = writer;
    }

    public BufferedReader getReader() {
        return reader;
    }

    public Writer getWriter() {
        return writer;
    }

    public String readLine(String prompt) {
        try {
            return reader.readLine();
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
    }

    public void write(String text) {
        try {
            writer.write(text);
            writer.flush();
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
    }

    public static StreamPairTerminal getDefault() {
        return new StreamPairTerminal(new InputStreamReader(System.in),
                                      new OutputStreamWriter(System.out));
    }

}
