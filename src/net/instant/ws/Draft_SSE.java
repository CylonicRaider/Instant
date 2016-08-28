package net.instant.ws;

import java.util.List;
import net.instant.util.Util;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;

/**
 * This is actually a hack implementing server-sent events. :P
 */
public class Draft_SSE extends Draft_Raw {

    @Override
    public HandshakeState acceptHandshakeAsServer(ClientHandshake handshakedata) {
        if (super.acceptHandshakeAsServer(handshakedata) != HandshakeState.MATCHED)
            return HandshakeState.NOT_MATCHED;
        // Just force an appropriate Accept header.
        List<Util.HeaderEntry> values = Util.parseHTTPHeader(handshakedata.getFieldValue("Accept"));
        if (values == null)
            return HandshakeState.NOT_MATCHED;
        for (Util.HeaderEntry ent : values) {
            String val = ent.getValue();
            if (val.equals("*/*") || val.equals("text/*") || val.equals("text/event-stream"))
                return HandshakeState.MATCHED;
        }
        return HandshakeState.NOT_MATCHED;
    }

    @Override
    public HandshakeBuilder postProcessHandshakeResponseAsServer(ClientHandshake request, ServerHandshakeBuilder response) {
        HandshakeBuilder ret = super.postProcessHandshakeResponseAsServer(request, response);
        response.put("Connection", "keep-alive");
        response.put("Content-Type", "text/event-stream");
        response.put("Cache-Control", "no-cache");
        return ret;
    }
    @Override
    public Draft copyInstance() {
        return new Draft_SSE();
    }

}
