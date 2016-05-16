package net.instant;

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
                                   InformationCollector.Datum info,
                                   ClientHandshake request,
                                   ServerHandshakeBuilder response,
                                   Handshakedata eff_resp) {
        if (! (parent.getEffectiveDraft(info) instanceof Draft_Raw)) return;
        info.setResponseInfo(response, (short) 404, "Not Found",
                             RESPONSE.length());
        response.put("Content-Type", "text/plain; charset=utf-8");
        parent.assign(info, this);
    }

    public void onOpen(InformationCollector.Datum info,
                       WebSocket conn, ClientHandshake handshake) {
        conn.send(RESPONSE);
        conn.close();
    }

}
