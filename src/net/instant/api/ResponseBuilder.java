package net.instant.api;

import org.json.JSONObject;

/**
 * HTTP response builder.
 * Also includes setters for some RequestData properties.
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
    void putHeader(String name, String content);

    /**
     * Fabricate a new cookie with the given name and value.
     * Metadata may be set using the put() method.
     */
    Cookie makeCookie(String name, String value);

    /**
     * Fabricate a new cookie with the given name and data.
     * Metadata may be amended using the put() method().
     */
    Cookie makeCookie(String name, JSONObject data);

    /**
     * Add an HTTP response header serializing the cookie.
     */
    void putCookie(Cookie cookie);

}
