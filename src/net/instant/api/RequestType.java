package net.instant.api;

/**
 * The actual protocol a request uses.
 */
public enum RequestType {

    /**
     * A plain old HTTP request.
     */
    HTTP,

    /**
     * A WebSocket connection.
     */
    WS,

    /**
     * A server-sent event stream.
     */
    SSE

}
