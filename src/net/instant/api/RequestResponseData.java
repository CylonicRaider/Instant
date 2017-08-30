package net.instant.api;

import java.util.List;
import java.util.Map;

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
     * A read-only mapping of response headers.
     * Multiple values per name are not supported.
     */
    Map<String, String> getResponseHeaders();

    /**
     * Convenience function to obtain a single HTTP response header.
     */
    String getResponseHeader(String name);

    /**
     * A list of cookies sumbitted to the client.
     * Includes those of all plugins and the core.
     */
    List<Cookie> getResponseCookies();

}
