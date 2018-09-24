package net.instant.console;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class CapturingWriter extends Writer {

    public interface Listener {

        void outputWritten(Event event);

    }

    public class Event {

        private final String text;

        public Event(String text) {
            this.text = text;
        }

        public CapturingWriter getParent() {
            return CapturingWriter.this;
        }

        public String getText() {
            return text;
        }

    }

    private final List<Listener> listeners;

    public CapturingWriter() {
        listeners = new ArrayList<Listener>();
    }

    public synchronized Listener[] getListeners() {
        return listeners.toArray(new Listener[listeners.size()]);
    }

    public synchronized void addListener(Listener l) {
        listeners.add(l);
    }

    public synchronized void removeListener(Listener l) {
        listeners.remove(l);
    }

    protected synchronized void fireEvent(Event evt) {
        for (Listener l : getListeners()) {
            l.outputWritten(evt);
        }
    }

    public void write(char[] data, int offset, int length) {
        fireEvent(new Event(new String(data, offset, length)));
    }

    public void flush() {
        /* NOP -- We don't buffer anything. */
    }

    public void close() {
        /* NOP -- We will be GC-ed. */
    }

}
