package net.instant.ws;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.instant.api.RequestResponseData;

public class ConnectionGC implements Runnable {

    public static final long INTERVAL = 10000;
    public static final long GRACE_TIME = 5000;

    private final InstantWebSocketServer parent;
    private final Map<RequestResponseData, Long> deadlines;

    public ConnectionGC(InstantWebSocketServer parent) {
        this.parent = parent;
        this.deadlines = Collections.synchronizedMap(
            new HashMap<RequestResponseData, Long>());
    }

    public InstantWebSocketServer getParent() {
        return parent;
    }

    public Map<RequestResponseData, Long> getDeadlines() {
        synchronized (deadlines) {
            return new HashMap<RequestResponseData, Long>(deadlines);
        }
    }

    public Long getDeadline(RequestResponseData connection) {
        return deadlines.get(connection);
    }

    public void setDeadline(RequestResponseData connection, long deadline) {
        deadlines.put(connection, deadline);
    }

    public void removeDeadline(RequestResponseData connection) {
        deadlines.remove(connection);
    }

    public void run() {
        for (;;) {
            long now = System.currentTimeMillis();
            for (Map.Entry<RequestResponseData, Long> e :
                 getDeadlines().entrySet()) {
                if (e.getValue() != null && e.getValue() < now)
                    e.getKey().getConnection().close();
            }
            try {
                Thread.sleep(INTERVAL);
            } catch (InterruptedException exc) {
                break;
            }
        }
    }

}
