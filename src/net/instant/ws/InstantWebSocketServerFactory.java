package net.instant.ws;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.ExecutorService;
import net.instant.ws.ssl.SSLEngineFactory;
import org.java_websocket.SSLSocketChannel2;
import org.java_websocket.WebSocketAdapter;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.WebSocketServerFactory;
import org.java_websocket.drafts.Draft;

public class InstantWebSocketServerFactory implements WebSocketServerFactory {

    private final SSLEngineFactory sslef;
    private final ExecutorService executor;

    public InstantWebSocketServerFactory(SSLEngineFactory sslef,
                                         ExecutorService executor) {
        this.sslef = sslef;
        this.executor = executor;
    }
    public InstantWebSocketServerFactory() {
        this(null, null);
    }

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
        if (sslef == null) return channel;
        return new SSLSocketChannel2(channel, sslef.createSSLEngine(false),
                                     executor, key);
    }

    @Override
    public void close() {
        if (executor != null) executor.shutdown();
    }

}
