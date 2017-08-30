package net.instant.api;

import java.nio.ByteBuffer;

/**
 * A hook intercepting HTTP requests (of any kind).
 * Whether WebSocket requests technically count as HTTP or not, they are
 * treated equalls as far as the backend is concerned.
 */
public interface RequestHook {

    /**
     * Evaluate and possibly process a request.
     * If the return value is true, the hook is assumed to be in charge of
     * the request and no further handling happens.
     * Because of structural constraints, the response headers and the body
     * of a plain HTTP reply must be sent separately; headers are submitted
     * in evaluateRequest(), the body is sent in onOpen() (and/or
     * subsequently).
     * Individual requests can be identified using the RequestData object,
     * which is guaranteed to be identical with the ClientConnection
     * objects below for a single request. (resp is identical to it as well,
     * but displays a different method set.)
     */
    boolean evaluateRequest(RequestData req, ResponseBuilder resp);

    /**
     * Handle a ready output channel.
     * For plain HTTP requests, this is the place where the response body
     * is sent; for WebSockets, this corresponds to a fully established
     * connection.
     * Depending on the request type, data sent to the enclosed WebSocket
     * instance may be formatted as actual WebSocket messages, or as raw
     * data (in spite of the class name).
     */
    void onOpen(ClientConnection req);

    /**
     * Handle binary data from the client.
     * For plain HTTP requests, this corresponds to a chunk of the request
     * body; for WebSockets, this is a (binary) message incoming.
     * Should not be confused with MessageHook.onMessage(), which can
     * intercept "messages" as well.
     */
    void onInput(ClientConnection req, ByteBuffer data);

    /**
     * Handle string data from the client.
     * Only for WebSockets, this corresponds to a text frame from the client.
     * Should not be confused with MessageHook.onMessage(), which can
     * intercept "messages" as well.
     */
    void onInput(ClientConnection req, String data);

    /**
     * Handle a closed connection.
     * Per-request resources should be cleaned up here.
     * normal indicates whether the close is orderly or abnormal.
     */
    void onClose(ClientConnection req, boolean normal);

    /**
     * Handle an error condition.
     * req may be null, indicating a (fatal and global) error in the core
     * itself.
     * The exception object passed contains details.
     * The method is called in two cases, once when an error happens during
     * connection (so that onOpen() or onClose() are never called), or when
     * a connection is already established. In the latter case, an onClose()
     * call may follow shortly if the error was fatal to the connection, or
     * not if it was not (most likely an application error).
     * To clarify, after an onOpen(), an onClose() will always be called, or
     * the whole backend will go down.
     */
    void onError(ClientConnection req, Exception exc);

}
