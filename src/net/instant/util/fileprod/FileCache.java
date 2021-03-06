package net.instant.util.fileprod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FileCache implements Producer {

    private final Map<String, FileCell> data;

    public FileCache() {
        data = new HashMap<String, FileCell>();
    }

    public synchronized void add(FileCell cell) {
        data.put(cell.getName(), cell);
    }
    public synchronized void remove(FileCell cell) {
        data.remove(cell.getName());
    }

    public synchronized FileCell getEx(String name) {
        return data.get(name);
    }
    public synchronized FileCell get(String name) {
        FileCell cell = getEx(name);
        if (cell != null && ! cell.isValid()) {
            remove(cell);
            return null;
        }
        return cell;
    }

    public synchronized void clear() {
        data.clear();
    }

    public ProducerJob produce(String name) {
        final FileCell cell = get(name);
        if (cell == null) return null;
        return new ProducerJob(name) {
            public FileCell produce() {
                return cell;
            }
        };
    }

    public synchronized void gc() {
        Iterator<FileCell> it = data.values().iterator();
        while (it.hasNext()) {
            FileCell c = it.next();
            if (! c.isValid()) it.remove();
        }
    }

}
