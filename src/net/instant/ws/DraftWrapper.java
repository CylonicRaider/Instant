package net.instant.ws;

import java.nio.ByteBuffer;
import java.util.List;
import net.instant.api.RequestType;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.exceptions.LimitExedeedException;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ClientHandshakeBuilder;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;

public class DraftWrapper extends Draft {

    public interface Hook {

        void postProcess(ClientHandshake request, ServerHandshakeBuilder response, HandshakeBuilder result) throws InvalidHandshakeException;

        void handleRequestLine(Handshakedata handshake, String line);

    }

    private final Draft wrapped;
    private Hook hook;

    public DraftWrapper(Draft w, Hook h) {
        wrapped = w;
        hook = h;
    }
    public DraftWrapper(Draft wrapped) {
        this(wrapped, null);
    }

    @Override
    public HandshakeState acceptHandshakeAsClient(ClientHandshake request, ServerHandshake response) throws InvalidHandshakeException {
        return wrapped.acceptHandshakeAsClient(request, response);
    }

    @Override
    public HandshakeState acceptHandshakeAsServer(ClientHandshake handshakedata) throws InvalidHandshakeException {
        return wrapped.acceptHandshakeAsServer(handshakedata);
    }

    @Override
    public ByteBuffer createBinaryFrame(Framedata framedata) {
        return wrapped.createBinaryFrame(framedata);
    }

    @Override
    public List<Framedata> createFrames(ByteBuffer binary, boolean mask) {
        return wrapped.createFrames(binary, mask);
    }

    @Override
    public List<Framedata> createFrames(String text, boolean mask) {
        return wrapped.createFrames(text, mask);
    }

    @Override
    public HandshakeBuilder postProcessHandshakeResponseAsServer(ClientHandshake request, ServerHandshakeBuilder response) throws InvalidHandshakeException {
        HandshakeBuilder ret = wrapped.postProcessHandshakeResponseAsServer(request, response);
        if (hook != null) hook.postProcess(request, response, ret);
        return ret;
    }

    @Override
    public ClientHandshakeBuilder postProcessHandshakeRequestAsClient(ClientHandshakeBuilder request) throws InvalidHandshakeException {
        return wrapped.postProcessHandshakeRequestAsClient(request);
    }

    @Override
    public void reset() {
        wrapped.reset();
    }

    @Override
    public List<Framedata> translateFrame(ByteBuffer buffer) throws InvalidDataException, LimitExedeedException {
        return wrapped.translateFrame(buffer);
    }

    @Override
    public CloseHandshakeType getCloseHandshakeType() {
        return wrapped.getCloseHandshakeType();
    }

    @Override
    public Draft copyInstance() {
        return new DraftWrapper(getWrapped().copyInstance(), getHook());
    }

    @Override
    public Handshakedata translateHandshake(ByteBuffer buf) throws InvalidHandshakeException {
        String header = readStringLine(buf);
        buf.reset();
        Handshakedata ret = super.translateHandshake(buf);
        if (hook != null) hook.handleRequestLine(ret, header);
        return ret;
    }

    public Draft getWrapped() {
        return wrapped;
    }
    public Hook getHook() {
        return hook;
    }
    public void setHook(Hook h) {
        hook = h;
    }

    public static RequestType getRequestType(Draft draft) {
        if (draft instanceof DraftWrapper) {
            return getRequestType(((DraftWrapper) draft).getWrapped());
        } else if (draft instanceof Draft_SSE) {
            return RequestType.SSE;
        } else if (draft instanceof Draft_Error) {
            return RequestType.ERROR;
        } else if (draft instanceof Draft_Raw) {
            return RequestType.HTTP;
        } else {
            return RequestType.WS;
        }
    }



}
