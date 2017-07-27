package net.instant.ws;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.instant.api.RequestResponseData;

public class ConnectionGC implements Runnable {

    private final Map<RequestResponseData, Long> deadlines;

    public ConnectionGC(InstantWebSocketServer parent) {
        deadlines = Collections.synchronizedMap(
            new HashMap<RequestResponseData, Long>());
    }

    public Map<RequestResponseData, Long> getDeadlines() {
        return Collections.unmodifiableMap(deadlines);
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
        /* TODO */
    }

}
