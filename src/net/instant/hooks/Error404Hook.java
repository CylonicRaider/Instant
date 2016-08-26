package net.instant.hooks;

import net.instant.info.RequestInfo;
import net.instant.ws.Draft_Raw;
import net.instant.ws.InstantWebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;

public class Error404Hook extends HookAdapter {

    public static final String RESPONSE = "404 not found";

    public boolean allowUnassigned() {
        return false;
    }

    public void postProcessRequest(InstantWebSocketServer parent,
                                   RequestInfo info,
                                   Handshakedata eff_resp) {
        if (! (parent.getEffectiveDraft(info) instanceof Draft_Raw)) return;
        info.respond((short) 404, "Not Found", RESPONSE.length());
        info.putHeader("Content-Type", "text/plain; charset=utf-8");
        parent.assign(info, this);
    }

    public void onOpen(RequestInfo info, ClientHandshake handshake) {
        info.getConnection().send(RESPONSE);
        info.getConnection().close();
    }

}
