package net.instant.api;

import org.java_websocket.WebSocket;

/**
 * A fully established connection to a client.
 */
public interface ClientConnection extends RequestResponseData {

    /**
     * UNIX timestamp of when to close the connection.
     * The resolution is a millisecond, but the actual closing may happen at
     * much larger intervals. Can be overridden by subsequent setDeadline()
     * calls. null denotes an indefinite lifetime.
     */
    Long getDeadline();

    /**
     * Set (or clear) the connection closing timer.
     * See getDeadline() for details.
     */
    void setDeadline(Long time);

    /**
     * The data channel to the client.
     * Should usually not be used directly, except for its send() methods.
     */
    WebSocket getConnection();

}
