package net.instant.ws;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import org.java_websocket.WebSocketAdapter;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.WebSocketServerFactory;
import org.java_websocket.drafts.Draft;

public class InstantWebSocketServerFactory implements WebSocketServerFactory {

    @Override
    public WebSocketImpl createWebSocket(WebSocketAdapter adapter,
                                         Draft draft) {
        return new InstantWebSocketImpl(adapter, draft);
    }

    @Override
    public WebSocketImpl createWebSocket(WebSocketAdapter adapter,
                                         List<Draft> drafts) {
        return new InstantWebSocketImpl(adapter, drafts);
    }

    @Override
    public ByteChannel wrapChannel(SocketChannel channel,
                                   SelectionKey key) throws IOException {
        return channel;
    }

    @Override
    public void close() {
        /* NOP */
    }

}
