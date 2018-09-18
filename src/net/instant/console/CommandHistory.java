package net.instant.console;

import java.util.ArrayList;
import java.util.List;

public class CommandHistory {

    private final List<String> entries;

    public CommandHistory() {
        entries = new ArrayList<String>();
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized String get(int index) {
        return entries.get(index);
    }

    public synchronized void add(String entry) {
        if (! entries.isEmpty() &&
                entry.equals(entries.get(entries.size() - 1)))
            return;
        entries.add(entry);
    }

}
