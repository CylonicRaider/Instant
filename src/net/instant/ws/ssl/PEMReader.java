package net.instant.ws.ssl;

import java.io.Closeable;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.instant.util.Encodings;
import net.instant.util.Util;

public class PEMReader implements Closeable, Iterable<PEMReader.PEMObject> {

    public static class PEMParsingException extends IOException {

        public PEMParsingException() {
            super();
        }
        public PEMParsingException(String message) {
            super(message);
        }
        public PEMParsingException(Throwable cause) {
            super(cause);
        }
        public PEMParsingException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    public static class PEMObject {

        private final String label;
        private final ByteBuffer data;

        public PEMObject(String label, byte[] data) {
            this.label = label;
            this.data = ByteBuffer.wrap(data);
        }

        public String getLabel() {
            return label;
        }

        public ByteBuffer getData() {
            return data.asReadOnlyBuffer();
        }

        public byte[] getDataBytes() {
            // The getData() call avoids changing the underlying buffer's
            // position.
            return Util.extractBytes(getData());
        }

    }

    private class PEMIterator implements Iterator<PEMObject> {

        public boolean hasNext() {
            try {
                return (peekPEMObject() != null);
            } catch (IOException exc) {
                throw new RuntimeException(exc);
            }
        }

        public PEMObject next() {
            try {
                return readPEMObject();
            } catch (IOException exc) {
                throw new RuntimeException(exc);
            }
        }

        public void remove() {
            throw new UnsupportedOperationException(
                "Cannot remove from PEMReader");
        }

    }

    private static final Pattern ENCAPSULATION_LINE =
        Pattern.compile("-----\\s*(BEGIN|END)" +
            "(?:\\s+([!-,.-~](?:[\\s-]?[!-,.-~])*))?\\s*-----\\s*");

    private final LineNumberReader reader;
    private final PEMIterator iterator;
    private PEMObject buffered;

    public PEMReader(Reader reader) {
        this.reader = (reader instanceof LineNumberReader) ?
            (LineNumberReader) reader : new LineNumberReader(reader);
        this.iterator = new PEMIterator();
    }

    public synchronized PEMObject peekPEMObject() throws IOException {
        if (buffered != null) return buffered;
        String label = null;
        StringBuilder buffer = new StringBuilder();
        for (;;) {
            String line = reader.readLine();
            if (line == null) {
                if (label != null)
                    throw new PEMParsingException("PEM object truncated");
                return null;
            }
            Matcher m = ENCAPSULATION_LINE.matcher(line);
            if (! m.matches()) {
                if (label != null)
                    buffer.append(line);
                continue;
            }
            String newLabel = nullToEmpty(m.group(2));
            if (label == null) {
                if (m.group(1).equals("END"))
                    throw new PEMParsingException("Unmatched PEM END line " +
                        reader.getLineNumber());
                label = newLabel;
            } else {
                if (m.group(1).equals("START"))
                    throw new PEMParsingException("Nested PEM START line " +
                        reader.getLineNumber());
                if (! newLabel.equals(label))
                    throw new PEMParsingException("PEM END line " +
                        reader.getLineNumber() +
                        " label does not match previous START line");
                break;
            }
        }
        byte[] decodedData;
        try {
            decodedData = Encodings.fromBase64(buffer.toString());
        } catch (IllegalArgumentException exc) {
            throw new PEMParsingException("Invalid PEM base64 payload", exc);
        }
        buffered = new PEMObject(label, decodedData);
        return buffered;
    }

    public synchronized PEMObject readPEMObject() throws IOException {
        PEMObject ret = peekPEMObject();
        buffered = null;
        return ret;
    }

    public Iterator<PEMObject> iterator() {
        return iterator;
    }

    public void close() throws IOException {
        reader.close();
    }

    private static final String nullToEmpty(String s) {
        return (s == null) ? "" : s;
    }

}
