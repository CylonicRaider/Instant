package net.instant.console.util;

import java.util.ArrayList;
import java.util.List;

public class CommandHistory {

    public interface Listener {

        void historyChanged(CommandHistory history);

    }

    private final List<String> entries;
    private final List<Listener> listeners;

    public CommandHistory() {
        entries = new ArrayList<String>();
        listeners = new ArrayList<Listener>();
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized String get(int index) {
        if (index < 0 || index >= entries.size()) return null;
        return entries.get(index);
    }

    public synchronized void add(String entry) {
        if (! entries.isEmpty() &&
                entry.equals(entries.get(entries.size() - 1)))
            return;
        entries.add(entry);
        fireEvent();
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

    protected synchronized void fireEvent() {
        // NOTE: Some code relies on this always increasing the history size
        //       by exactly 1.
        for (Listener l : getListeners()) {
            l.historyChanged(this);
        }
    }

}
