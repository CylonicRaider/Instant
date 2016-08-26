package net.instant.hooks;

import java.nio.ByteBuffer;
import net.instant.info.RequestInfo;
import net.instant.ws.InstantWebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;

public abstract class HookAdapter implements InstantWebSocketServer.Hook {

    public abstract boolean allowUnassigned();

    public Boolean verifyWebSocket(ClientHandshake request,
                                   boolean guess) { return null; }

    public void postProcessRequest(InstantWebSocketServer parent,
                                   RequestInfo info,
                                   Handshakedata eff_resp) {}

    public void onOpen(RequestInfo info, ClientHandshake handshake) {}
    public void onClose(RequestInfo info, int code, String reason,
                        boolean remote) {}
    public void onMessage(RequestInfo info, String message) {}
    public void onMessage(RequestInfo info, ByteBuffer message) {}
    public void onError(RequestInfo info, Exception ex) {}


}
