package net.instant.ws;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import net.instant.info.InformationCollector;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.exceptions.LimitExedeedException;
import org.java_websocket.exceptions.NotSendableException;
import org.java_websocket.framing.FrameBuilder;
import org.java_websocket.framing.Framedata;
import org.java_websocket.framing.FramedataImpl1;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ClientHandshakeBuilder;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.util.Charsetfunctions;

public class DraftWrapper extends Draft {

    private final Draft wrapped;
    private InformationCollector collector;

    public DraftWrapper(Draft w, InformationCollector c) {
        wrapped = w;
        collector = c;
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
        if (collector != null) collector.postProcess(request, response, ret);
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
        return new DraftWrapper(getWrapped(), getCollector());
    }

    @Override
    public Handshakedata translateHandshake(ByteBuffer buf) throws InvalidHandshakeException {
        String header = readStringLine(buf);
        buf.reset();
        Handshakedata ret = super.translateHandshake(buf);
        if (collector != null) collector.addRequestLine(ret, header);
        return ret;
    }

    public Draft getWrapped() {
        return wrapped;
    }
    public InformationCollector getCollector() {
        return collector;
    }
    public void setCollector(InformationCollector c) {
        collector = c;
    }

}
