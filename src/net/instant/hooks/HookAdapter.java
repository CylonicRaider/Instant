package net.instant.hooks;

import java.nio.ByteBuffer;
import net.instant.InstantWebSocketServer;
import net.instant.info.Datum;
import net.instant.info.InformationCollector;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;

public abstract class HookAdapter implements InstantWebSocketServer.Hook {

    public abstract boolean allowUnassigned();

    public Boolean verifyWebSocket(ClientHandshake request,
                                   boolean guess) { return null; }

    public void postProcessRequest(InstantWebSocketServer parent,
                                   Datum info,
                                   ClientHandshake request,
                                   ServerHandshakeBuilder response,
                                   Handshakedata eff_resp) {}

    public void onOpen(Datum info, WebSocket conn,
                       ClientHandshake handshake) {}
    public void onClose(WebSocket conn, int code, String reason,
                        boolean remote) {}
    public void onMessage(WebSocket conn, String message) {}
    public void onMessage(WebSocket conn, ByteBuffer message) {}
    public void onError(WebSocket conn, Exception ex) {}


}
