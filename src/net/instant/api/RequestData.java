package net.instant.api;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

/**
 * Generic information about an incoming HTTP request.
 * The fields follow roughly the Apache Combined Log Format.
 */
public interface RequestData {

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
     * The address the request originated from.
     */
    InetSocketAddress getAddress();

    /**
     * The RFC 1413 identity of the remote peer.
     * Most probably not available and thus null.
     */
    String getRFC1413Identity();

    /**
     * The authenticated identity of the request.
     * E.g. an account name.
     * Not implemented by the core, but possibly by plugins.
     * null if not available (e.g. for an anonymous user).
     */
    String getAuthIdentity();

    /**
     * The UNIX time at which the request arrived.
     */
    long getTimestamp();

    /**
     * The HTTP request method.
     */
    String getMethod();

    /**
     * The HTTP request path.
     */
    String getPath();

    /**
     * The HTTP request version.
     */
    String getHTTPVersion();

    /**
     * The value of the HTTP Referer header.
     * The mis-spelling of the header name has been perpetuated by the
     * corresponding standard.
     */
    String getReferrer();

    /**
     * The value of the HTTP User-Agent header.
     */
    String getUserAgent();

    /**
     * The type of the request.
     * Plugins should check what kind of resource the client requests, and
     * only accept the connection if they can deal with it.
     */
    RequestType getRequestType();

    /**
     * A read-only mapping of HTTP request headers.
     * Repeated headers are not supported.
     */
    Map<String, String> getHeaders();

    /**
     * Convenience function to obtain a single HTTP header value.
     */
    String getHeader(String name);

    /**
     * Return a list of all cookies submitted with the request.
     * Since the Cookie HTTP header does only transmit key-value pairs,
     * metadata may have to be amended when sending the cookie back.
     */
    List<Cookie> getCookies();

    /**
     * Convenience function to obtain a single cookie.
     */
    Cookie getCookie(String name);

    /**
     * Additional meta-data about the request.
     * The mapping maintains insertion order and is read-write. Data stored
     * here will be displayed in the HTTP request log.
     */
    Map<String, Object> getExtraData();

    /**
     * More additional meta-data about the request.
     * Similarly to getExtraData(), the mapping maintains insertion order and
     * is read-write; dissimilarly, its contents are not flushed into the
     * HTTP log. Note that this mapping is shared between all plugins.
     */
    Map<String, Object> getPrivateData();

    /**
     * Determine the identity of the user causing the request.
     * The return value, if not null, is the identity cookie, which is
     * automatically added to the ResponseBuilder corresponding to this
     * RequestData. See the values of IdentMode for details.
     */
    Cookie identify(IdentMode mode);

}
