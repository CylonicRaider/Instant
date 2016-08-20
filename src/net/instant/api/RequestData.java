package net.instant.api;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Generic information about an incoming HTTP request.
 * The fields follow roughly the Apache Combined Log Format.
 */
public interface RequestData {

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
     * Not implemented by the backend, but possibly by plugins.
     * null if not available (e.g. for an anonymous user).
     */
    String getAuthIdentity();

    /**
     * The UNIX time at which the request arrived.
     */
    long getTimestamp();

    /**
     * The HTTP request line.
     * Since standard-abiding lines should be trivial to parse, a more
     * detailed view is not provided to reduce the interface size.
     * E.g. "GET / HTTP/1.1".
     */
    String getRequestLine();

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
     * An array of HTTP request header names.
     * Repeated headers are not supported.
     */
    String[] listHeaders();

    /**
     * The value of the header with the given name, or null if none.
     * To check for a header's presence, null-check the return value.
     */
    String getHeader(String name);

    /**
     * Additional meta-data about the request.
     * Not being expressed by the interface, the mapping maintains insertion
     * order and is read-write.
     */
    Map<String, Object> getExtraData();

}
