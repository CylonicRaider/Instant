package net.instant.console.util;

import java.io.Writer;

/* A Writer that writes to some medium that is not expected to fail (such as
 * a GUI buffer) and which does not buffer its output (therefore not needing
 * flushing or closing). */
public abstract class VirtualWriter extends Writer {

    public abstract void write(char[] data, int offset, int length);

    public void flush() {}

    public void close() {}

}
