package net.instant.ws;

import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;

/**
 * A hack for sending error messages over plain HTTP.
 */
public class Draft_Error extends Draft_Raw {

    @Override
    public HandshakeState acceptHandshakeAsClient(ClientHandshake request, ServerHandshake response) {
        return HandshakeState.MATCHED;
    }

    @Override
    public HandshakeState acceptHandshakeAsServer(ClientHandshake handshakedata) {
        return HandshakeState.MATCHED;
    }

    @Override
    public Draft copyInstance() {
        return new Draft_Error();
    }

}
