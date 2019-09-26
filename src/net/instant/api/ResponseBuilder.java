package net.instant.api;

import java.util.List;
import org.json.JSONObject;

/**
 * HTTP response builder.
 * Also includes setters for some RequestData properties, which allow refining
 * the corresponding HTTP log fields.
 */
public interface ResponseBuilder {

    /** Mode selector for identify(); see there. */
    public enum IdentMode {
        /**
         * Return identification data if already allocated.
         */
        OPTIONAL,
        /**
         * Create identification data if necessary.
         * The data property of identify()'s return value includes a
         * string-encoded UUID, which identifies the client, at the "uuid"
         * key; additionally, the UUID is exposed (as a java.util.UUID
         * instance) in the extraData property at the same key.
         */
        ALWAYS,
        /**
         * Like ALWAYS, but also assign the connection an individual ID.
         * The individual ID is available at the "id" key in extraData.
         */
        INDIVIDUAL
    }

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

    /**
     * Determine the identity of the user causing the request.
     * The return value, if not null, is the identity cookie, which is
     * automatically added to the ResponseBuilder corresponding to this
     * RequestData. See the values of IdentMode for details.
     */
    Cookie identify(IdentMode mode);

}
