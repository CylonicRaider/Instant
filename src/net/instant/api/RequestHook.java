package net.instant.api;

import java.nio.ByteBuffer;
import org.java_websocket.WebSocket;

/**
 * A hook intercepting HTTP requests (of any kind).
 */
public interface RequestHook {

    /**
     * Evaluate and possibly process a request.
     * If the return value is true, the hook is assumed to be in charge of
     * the request and no further handling happens.
     * Because of structural constraints, the response headers and the body
     * of a plain HTTP reply must be sent separately; headers are submitted
     * in evaluateRequest(), the body is sent in onOpen().
     * Individual requests can be identified using the RequestData object,
     * which is guaranteed to be identical with the RequestResponseData
     * objects below for a single request.
     */
    boolean evaluateRequest(RequestData req, ResponseBuilder resp);

    /**
     * Handle a ready output channel.
     * For plain HTTP requests, this is the place where the response body
     * is sent; for WebSockets, this corresponds to a fully estabilished
     * connection.
     * In spice of the class name, the WebSocket instance can be used to
     * send raw data, depending on the request type.
     */
    void onOpen(RequestResponseData req);

    /**
     * Handle binary data from the client.
     * For plain HTTP requests, this corresponds to a chunk of the request
     * body; for WebSockets, this is a (binary) message incoming.
     * Should not be confused with MessageHook.onMessage(), which can
     * intercept "messages" as well.
     */
    void onInput(RequestResponseData req, ByteBuffer data);

    /**
     * Handle string data from the client.
     * Only for WebSockets, this corresponds to a text frame from the client.
     * Should not be confused with MessageHook.onMessage(), which can
     * intercept "messages" as well.
     */
    void onInput(RequestResponseData req, String data);

    /**
     * Handle a closed connection.
     * Per-request resources should be cleaned up here.
     */
    void onClose(RequestResponseData req);

}
