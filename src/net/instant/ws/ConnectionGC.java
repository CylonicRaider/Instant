package net.instant.ws;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import net.instant.api.ClientConnection;

public class ConnectionGC implements Runnable {

    private static final Logger LOGGER = Logger.getLogger("ConnGC");

    public static final long INTERVAL = 10000;
    public static final long GRACE_TIME = 5000;

    private final Map<ClientConnection, Long> deadlines;

    public ConnectionGC() {
        this.deadlines = Collections.synchronizedMap(
            new HashMap<ClientConnection, Long>());
    }

    public Map<ClientConnection, Long> getDeadlines() {
        synchronized (deadlines) {
            return new HashMap<ClientConnection, Long>(deadlines);
        }
    }

    public Long getDeadline(ClientConnection connection) {
        return deadlines.get(connection);
    }

    public void setDeadline(ClientConnection connection, long deadline) {
        deadlines.put(connection, deadline);
    }

    public void removeDeadline(ClientConnection connection) {
        deadlines.remove(connection);
    }

    public void cleanup(ClientConnection r) {
        LOGGER.info("Cleaned up connection (" + r.getConnection() + ")");
        r.getConnection().close();
    }

    public void run() {
        for (;;) {
            long now = System.currentTimeMillis();
            for (Map.Entry<ClientConnection, Long> e :
                 getDeadlines().entrySet()) {
                if (e.getValue() != null && e.getValue() < now) {
                    cleanup(e.getKey());
                }
            }
            try {
                Thread.sleep(INTERVAL);
            } catch (InterruptedException exc) {
                break;
            }
        }
    }

}