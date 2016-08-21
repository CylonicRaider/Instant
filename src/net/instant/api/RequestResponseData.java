package net.instant.api;

import java.util.List;
import org.java_websocket.WebSocket;

/**
 * Information about an HTTP request and the response to it.
 */
public interface RequestResponseData extends RequestData {

    /**
     * The HTTP response status code.
     */
    int getStatusCode();

    /**
     * The HTTP status message.
     */
    String getStatusMessage();

    /**
     * The response body length, or -1 if none.
     */
    long getResponseLength();

    /**
     * An array of response header names.
     * Multiple headers are not supported.
     */
    String[] listResponseHeaders();

    /**
     * The value of the response header with the given name, or null if none.
     * To check for a header's presence, null-check the return value.
     */
    String getResponseHeader(String name);

    /**
     * A list of cookies sumbitted to the client.
     * Includes those of all plugins and the core.
     */
    List<Cookie> getResponseCookies();

    /**
     * The data channel to the client.
     * Should usually not be used directly, except for its send() methods.
     */
    WebSocket getConnection();

}
