package net.instant.ws;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import org.java_websocket.WebSocketAdapter;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.drafts.Draft;

// We retrieve the addresses of the underlying socket in setSelectionKey()
// and expose them via a dedicated API. The retrieval happens inside
// setSelectionKey() because that is called synchronously inside the selector
// loop and therefore before the socket has a chance to be closed (which can
// happen before the address is needed). setSelectionKey() in particular is
// chosen because the selection key is bound to the underlying socket channel
// (and not some SSL wrapper which may or may not expose the necessary
// information and be used as the channel). We use a dedicated API because the
// "original" API is documented to return null when the socket is closed,
// which is just what we do not want.
public class InstantWebSocketImpl extends WebSocketImpl {

    private volatile Datum description;
    private volatile InetSocketAddress cachedLocalAddress;
    private volatile InetSocketAddress cachedRemoteAddress;

    public InstantWebSocketImpl(WebSocketAdapter adapter, Draft draft) {
        super(adapter, draft);
    }
    public InstantWebSocketImpl(WebSocketAdapter adapter,
                                List<Draft> drafts) {
        super(adapter, drafts);
    }

    public Datum getDescription() {
        return description;
    }
    public void setDescription(Datum desc) {
        description = desc;
    }

    public InetSocketAddress getCachedLocalAddress() {
        if (cachedLocalAddress == null)
            cachedLocalAddress = getLocalSocketAddress();
        return cachedLocalAddress;
    }

    public InetSocketAddress getCachedRemoteAddress() {
        if (cachedRemoteAddress == null)
            cachedRemoteAddress = getRemoteSocketAddress();
        return cachedRemoteAddress;
    }

    @Override
    public void setSelectionKey(SelectionKey key) {
        super.setSelectionKey(key);
        SelectableChannel c = key.channel();
        if (c instanceof SocketChannel) {
            Socket s = ((SocketChannel) c).socket();
            cachedLocalAddress =
                (InetSocketAddress) s.getLocalSocketAddress();
            cachedRemoteAddress =
                (InetSocketAddress) s.getRemoteSocketAddress();
        }
    }

}
