package net.instant.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/* FIXME: Add support for non-BMP characters and for fullwidth ones,
 *        and avoid damaging the mark in readLine(). */
public class LineColumnReader extends BufferedReader {

    public interface Coordinates {
        long getLine();

        long getColumn();

        long getCharacterIndex();
    }

    public static class CoordinatesBuilder implements Coordinates {

        private long line;
        private long column;
        private long characterIndex;

        public CoordinatesBuilder(long line, long column,
                                  long characterIndex) {
            this.line = line;
            this.column = column;
            this.characterIndex = characterIndex;
        }
        public CoordinatesBuilder(Coordinates other) {
            this(other.getLine(), other.getColumn(),
                 other.getCharacterIndex());
        }
        public CoordinatesBuilder() {
            this(1, 1, 0);
        }

        public long getLine() {
            return line;
        }
        public void setLine(long l) {
            line = l;
        }

        public long getColumn() {
            return column;
        }
        public void setColumn(long c) {
            column = c;
        }

        public long getCharacterIndex() {
            return characterIndex;
        }
        public void setCharacterIndex(long c) {
            characterIndex = c;
        }

        public void set(Coordinates other) {
            setLine(other.getLine());
            setColumn(other.getColumn());
            setCharacterIndex(other.getCharacterIndex());
        }

    }

    protected static class State extends CoordinatesBuilder {

        private boolean inNL;

        public State() {
            super();
        }
        public State(State other) {
            set(other);
        }

        public boolean isInNL() {
            return inNL;
        }
        public void setInNL(boolean i) {
            inNL = i;
        }

        public void set(State other) {
            super.set(other);
            setInNL(other.isInNL());
        }

        @SuppressWarnings("fallthrough")
        public void advance(char ch, int tabSize) {
            setCharacterIndex(getCharacterIndex() + 1);
            switch (ch) {
                case '\t':
                    setColumn((getColumn() + tabSize - 2) / tabSize *
                              tabSize + 1);
                    break;
                case '\n':
                    if (isInNL()) break;
                    // Intentionally falling through.
                case '\r':
                    setLine(getLine() + 1);
                    setColumn(1);
                    break;
                default:
                    setColumn(getColumn() + 1);
                    break;
            }
            setInNL(ch == '\r');
        }

    }

    public static final int DEFAULT_TAB_SIZE = 8;

    private static final int SKIP_BUFSIZE = 32768;

    private final State coords;
    private final State markCoords;
    private int tabSize;

    {
        coords = new State();
        markCoords = new State();
        tabSize = DEFAULT_TAB_SIZE;
    }

    public LineColumnReader(Reader in) {
        super(in);
    }
    public LineColumnReader(Reader in, int bufSize) {
        super(in, bufSize);
    }

    public Coordinates getCoordinates() {
        return coords;
    }
    public void getCoordinates(CoordinatesBuilder recipient) {
        synchronized (lock) {
            recipient.set(coords);
        }
    }

    public int getTabSize() {
        synchronized (lock) {
            return tabSize;
        }
    }
    public void setTabSize(int ts) {
        synchronized (lock) {
            tabSize = ts;
        }
    }

    public void mark(int readAheadLimit) throws IOException {
        synchronized (lock) {
            super.mark(readAheadLimit);
            markCoords.set(coords);
        }
    }

    public void reset() throws IOException {
        synchronized (lock) {
            super.reset();
            coords.set(markCoords);
        }
    }

    public int read() throws IOException {
        synchronized (lock) {
            int ret = super.read();
            if (ret != -1) coords.advance((char) ret, tabSize);
            return ret;
        }
    }

    public int read(char[] cbuf) throws IOException {
        synchronized (lock) {
            return read(cbuf, 0, cbuf.length);
        }
    }

    public int read(char[] cbuf, int offset, int length) throws IOException {
        synchronized (lock) {
            int ret = super.read(cbuf, offset, length);
            // On EOF, ret is -1 and the loop does not run at all.
            for (int i = offset, ei = offset + ret; i < ei; i++) {
                coords.advance(cbuf[i], tabSize);
            }
            return ret;
        }
    }

    public long skip(long count) throws IOException {
        synchronized (lock) {
            long skipped = 0;
            char[] buffer = new char[SKIP_BUFSIZE];
            while (count > SKIP_BUFSIZE) {
                int rd = read(buffer, 0, SKIP_BUFSIZE);
                if (rd == -1) return skipped;
                skipped += rd;
                count -= SKIP_BUFSIZE;
            }
            int rd = read(buffer, 0, (int) count);
            if (rd > 0) skipped += rd;
            return skipped;
        }
    }

    public String readLine() throws IOException {
        StringBuilder ret = new StringBuilder();
        synchronized (lock) {
            for (;;) {
                int ch = read();
                if (ch == -1) {
                    if (ret.length() == 0) return null;
                } else if (ch == '\n') {
                    /* NOP */
                } else if (ch == '\r') {
                    mark(1);
                    ch = read();
                    if (ch != '\n') reset();
                } else {
                    ret.append((char) ch);
                    continue;
                }
                break;
            }
        }
        return ret.toString();
    }

}
