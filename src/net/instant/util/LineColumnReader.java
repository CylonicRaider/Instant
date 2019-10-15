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

        public static final int DEFAULT_TAB_SIZE = 8;

        private long line;
        private long column;
        private long characterIndex;
        private boolean inNL;
        private int tabSize;

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

        public boolean isInNL() {
            return inNL;
        }
        public void setInNL(boolean i) {
            inNL = i;
        }

        public int getTabSize() {
            return tabSize;
        }
        public void setTabSize(int ts) {
            tabSize = ts;
        }

        public void set(Coordinates other) {
            setLine(other.getLine());
            setColumn(other.getColumn());
            setCharacterIndex(other.getCharacterIndex());
            setInNL(false);
        }
        public void set(CoordinatesBuilder other) {
            setLine(other.getLine());
            setColumn(other.getColumn());
            setCharacterIndex(other.getCharacterIndex());
            setInNL(other.isInNL());
            setTabSize(other.getTabSize());
        }

        @SuppressWarnings("fallthrough")
        public void advance(char ch) {
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
        public void advance(char[] data, int offset, int size) {
            for (int i = offset, ei = offset + size; i < ei; i++) {
                advance(data[i]);
            }
        }
        public void advance(CharSequence data, int offset, int size) {
            for (int i = offset, ei = offset + size; i < ei; i++) {
                advance(data.charAt(i));
            }
        }

    }

    private static final int SKIP_BUFSIZE = 32768;

    private final CoordinatesBuilder coords;
    private final CoordinatesBuilder markCoords;

    {
        coords = new CoordinatesBuilder();
        markCoords = new CoordinatesBuilder();
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
            return coords.getTabSize();
        }
    }
    public void setTabSize(int ts) {
        synchronized (lock) {
            coords.setTabSize(ts);
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
            if (ret != -1) coords.advance((char) ret);
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
            coords.advance(cbuf, offset, ret);
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
