package net.instant.ws;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.NotSendableException;
import org.java_websocket.framing.FrameBuilder;
import org.java_websocket.framing.Framedata;
import org.java_websocket.framing.FramedataImpl1;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ClientHandshakeBuilder;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.util.Charsetfunctions;

/**
 * A hack implementing raw HTTP transfers.
 */
public class Draft_Raw extends Draft {

    @Override
    public HandshakeState acceptHandshakeAsClient(ClientHandshake request, ServerHandshake response) {
        /* Explicitly disallow WebSockets... */
        if (request.hasFieldValue("Sec-WebSocket-Key") || request.hasFieldValue("Sec-WebSocket-Accept"))
            return HandshakeState.NOT_MATCHED;
        return HandshakeState.MATCHED;
    }

    @Override
    public HandshakeState acceptHandshakeAsServer(ClientHandshake handshakedata) {
        /* Still explicitly disallow WebSockets... */
        boolean r = handshakedata.hasFieldValue("Sec-WebSocket-Version");
        return (r) ? HandshakeState.NOT_MATCHED : HandshakeState.MATCHED;
    }

    @Override
    public ByteBuffer createBinaryFrame(Framedata framedata) {
        /* Text/binary are passed through; others are discarded. */
        switch (framedata.getOpcode()) {
            case TEXT: case BINARY: break;
            default: return ByteBuffer.allocate(0);
        }
        ByteBuffer src = framedata.getPayloadData();
        ByteBuffer nbuf = ByteBuffer.allocate(src.limit());
        nbuf.put(src);
        nbuf.flip();
        return nbuf;
    }

    @Override
    public List<Framedata> createFrames(ByteBuffer binary, boolean mask) {
        /* From Draft_10.java */
        FrameBuilder curframe = new FramedataImpl1();
        try {
            curframe.setPayload(binary);
        } catch (InvalidDataException e) {
            throw new NotSendableException(e);
        }
        curframe.setFin(true);
        curframe.setOptcode(Framedata.Opcode.BINARY);
        curframe.setTransferemasked(mask);
        return Collections.singletonList((Framedata) curframe);
    }

    @Override
    public List<Framedata> createFrames(String text, boolean mask) {
        /* From Draft_10.java */
        FrameBuilder curframe = new FramedataImpl1();
        try {
            curframe.setPayload(ByteBuffer.wrap(Charsetfunctions.utf8Bytes(text)));
        } catch (InvalidDataException e) {
            throw new NotSendableException(e);
        }
        curframe.setFin(true);
        curframe.setOptcode(Framedata.Opcode.TEXT);
        curframe.setTransferemasked(mask);
        return Collections.singletonList((Framedata) curframe);
    }

    @Override
    public HandshakeBuilder postProcessHandshakeResponseAsServer(ClientHandshake request, ServerHandshakeBuilder response) {
        response.setHttpStatus((short) 404);
        response.setHttpStatusMessage("Not Found");
        response.put("Connection", "close");
        return response;
    }

    @Override
    public ClientHandshakeBuilder postProcessHandshakeRequestAsClient(ClientHandshakeBuilder request) {
        request.put("Connection", "close");
        return request;
    }

    @Override
    public void reset() {
        /* NOP */
    }

    @Override
    public List<Framedata> translateFrame(ByteBuffer buffer) {
        return createFrames(buffer, false);
    }

    @Override
    public CloseHandshakeType getCloseHandshakeType() {
        return CloseHandshakeType.NONE;
    }

    @Override
    public Draft copyInstance() {
        return new Draft_Raw();
    }

}
