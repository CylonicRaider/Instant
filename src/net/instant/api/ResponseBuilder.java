package net.instant.api;

import java.util.List;
import org.json.JSONObject;

/**
 * HTTP response builder.
 * Also includes setters for some RequestData properties, which allow refining
 * the corresponding HTTP log fields.
 */
public interface ResponseBuilder {

    /**
     * Set the RFC 1413 identity of the request's peer.
     */
    void setRFC1413Identity(String identity);

    /**
     * Set the authenticated identity of the request's peer.
     */
    void setAuthIdentity(String identity);

    /**
     * Initiate an HTTP response.
     * code is the HTTP status code, message the corresponding message,
     * length is the length of the response or -1 if not available.
     */
    void respond(int code, String message, long length);

    /**
     * Add an HTTP response header.
     */
    void addHeader(String name, String content);

    /**
     * Fabricate a new cookie with the given name and value.
     * Metadata may be set using the put() method. To be actually sent, the
     * cookie must be passed to addResponseCookie() method.
     */
    Cookie makeCookie(String name, String value);

    /**
     * Fabricate a new cookie with the given name and data.
     * Metadata may be amended using the put() method. To be actually sent,
     * the cookie must be passed to addResponseCookie() method.
     */
    Cookie makeCookie(String name, JSONObject data);

    /**
     * The list of cookies to be sent as a response.
     * The list can be mutated, and is inspected when response headers are
     * sent.
     */
    List<Cookie> getResponseCookies();

    /**
     * Convenience function to retrieve the response cookie with the given
     * name, or null.
     */
    Cookie getResponseCookie(String name);

    /**
     * Convenience function to add an entry to the response cookie list.
     */
    void addResponseCookie(Cookie cookie);

}
