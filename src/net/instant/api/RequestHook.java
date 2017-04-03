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
     * is sent; for WebSockets, this corresponds to a fully established
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
     * normal indicates whether the close is orderly or abnormal.
     */
    void onClose(RequestResponseData req, boolean normal);

    /**
     * Handle an error condition.
     * req may be null, indicating a (fatal) error in the server itself.
     * The exception object passed contains details.
     * The method is called in two cases, once when an error happens during
     * connection (so that onOpen() or onClose() are never called), or when
     * a connection is already established. In the latter case, an onClose()
     * call may follow shortly if the error was fatal to the connection, or
     * not if it was not (most likely an application error).
     * To clarify, after an onOpen(), an onClose() will always be called, or
     * the whole core will go down.
     */
    void onError(RequestResponseData req, Exception exc);

}
