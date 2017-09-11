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
     * A server-sent event stream.
     */
    SSE,

    /**
     * A WebSocket connection.
     */
    WS,

    /**
     * A request that was not accepted by any hook.
     * Used internally to deliver error messages without WebSocket framing.
     */
    ERROR

}
