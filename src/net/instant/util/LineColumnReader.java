package net.instant.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import net.instant.api.parser.TextLocation;

/* FIXME: Add support for non-BMP characters and for fullwidth ones,
 *        and avoid damaging the mark in readLine(). */
public class LineColumnReader extends BufferedReader {

    public static class FixedLocation implements TextLocation {

        private final long line;
        private final long column;
        private final long characterIndex;

        public FixedLocation(long line, long column, long characterIndex) {
            this.line = line;
            this.column = column;
            this.characterIndex = characterIndex;
        }
        public FixedLocation(TextLocation other) {
            this(other.getLine(), other.getColumn(),
                 other.getCharacterIndex());
        }

        public String toString() {
            return String.format("line %d column %d (char %d)", getLine(),
                                 getColumn(), getCharacterIndex());
        }

        public boolean equals(Object other) {
            if (! (other instanceof TextLocation)) return false;
            TextLocation co = (TextLocation) other;
            return (line == co.getLine() &&
                    column == co.getColumn() &&
                    characterIndex == co.getCharacterIndex());
        }

        public int hashCode() {
            return (int) (line ^ line >>> 31 ^ column ^ column >>> 31 ^
                characterIndex ^ characterIndex >>> 31);
        }

        public long getLine() {
            return line;
        }

        public long getColumn() {
            return column;
        }

        public long getCharacterIndex() {
            return characterIndex;
        }

    }

    public static class LocationTracker implements TextLocation {

        public static final int DEFAULT_TAB_SIZE = 8;

        private long line;
        private long column;
        private long characterIndex;
        private boolean inNL;
        private int tabSize;

        public LocationTracker(long line, long column, long characterIndex,
                               boolean inNL, int tabSize) {
            this.line = line;
            this.column = column;
            this.characterIndex = characterIndex;
            this.inNL = inNL;
            this.tabSize = tabSize;
        }
        public LocationTracker(long line, long column, long characterIndex) {
            this(line, column, characterIndex, false, DEFAULT_TAB_SIZE);
        }
        public LocationTracker(TextLocation other) {
            this(other.getLine(), other.getColumn(),
                 other.getCharacterIndex());
        }
        public LocationTracker(LocationTracker other) {
            this(other.getLine(), other.getColumn(),
                 other.getCharacterIndex(), other.isInNL(),
                 other.getTabSize());
        }
        public LocationTracker() {
            this(1, 1, 0);
        }

        public String toString() {
            return String.format("%s@%h[line=%s,column=%s,char=%s,inNL=%s," +
                "tabSize=%s]", getClass().getName(), this, getLine(),
                getColumn(), getCharacterIndex(), isInNL(), getTabSize());
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

        public void set(TextLocation other) {
            setLine(other.getLine());
            setColumn(other.getColumn());
            setCharacterIndex(other.getCharacterIndex());
            setInNL(false);
        }
        public void set(LocationTracker other) {
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

    private final LocationTracker coords;
    private final LocationTracker markCoords;

    {
        coords = new LocationTracker();
        markCoords = new LocationTracker();
    }

    public LineColumnReader(Reader in) {
        super(in);
    }
    public LineColumnReader(Reader in, int bufSize) {
        super(in, bufSize);
    }

    public TextLocation getCoordinates() {
        return coords;
    }
    public void getCoordinates(LocationTracker recipient) {
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
