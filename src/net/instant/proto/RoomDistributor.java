package net.instant.proto;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.instant.api.MessageContents;
import net.instant.api.RequestResponseData;
import net.instant.api.Room;
import org.java_websocket.exceptions.WebsocketNotConnectedException;

public class RoomDistributor implements Room {

    private static final Logger LOGGER = Logger.getLogger("RoomDistr");

    private final MessageDistributor parent;
    private final String name;
    private final Set<RequestResponseData> conns;

    public RoomDistributor(MessageDistributor parent, String name) {
        this.parent = parent;
        this.name = name;
        conns = Collections.synchronizedSet(
            new HashSet<RequestResponseData>());
    }

    public String getName() {
        return name;
    }

    protected void add(RequestResponseData conn) {
        conns.add(conn);
    }

    protected void remove(RequestResponseData conn) {
        conns.remove(conn);
    }

    public void broadcast(ByteBuffer message) {
        RequestResponseData[] cl = getClientArray();
        for (RequestResponseData c : cl) {
            try {
                c.getConnection().send(message);
            } catch (WebsocketNotConnectedException exc) {
                // Should not happen!
                LOGGER.log(Level.WARNING, "Non-connected connection " +
                    "present (" + c.getConnection() + "); removing",
                    exc);
                parent.remove(c);
            }
        }
    }
    public void broadcast(String message) {
        RequestResponseData[] cl = getClientArray();
        for (RequestResponseData c : cl) {
            try {
                c.getConnection().send(message);
            } catch (WebsocketNotConnectedException exc) {
                // Should not happen, too!
                LOGGER.log(Level.WARNING, "Non-connected connection " +
                    "present (" + c.getConnection() + "); removing",
                    exc);
                parent.remove(c);
            }
        }
    }
    public void broadcast(MessageContents message) {
        broadcast(message.toString());
    }

    protected RequestResponseData[] getClientArray() {
        synchronized (conns) {
            return conns.toArray(new RequestResponseData[conns.size()]);
        }
    }
    public Set<RequestResponseData> getClients() {
        synchronized (conns) {
            return new HashSet<RequestResponseData>(conns);
        }
    }

    public String sendUnicast(RequestResponseData conn,
                              MessageContents message) {
        String id = MessageDistributor.makeID();
        message.setID(id);
        conn.getConnection().send(message.toString());
        return id;
    }
    public String sendBroadcast(MessageContents message) {
        String id = MessageDistributor.makeID();
        message.setID(id);
        broadcast(message);
        return id;
    }

    public MessageDistributor getGroup() {
        return parent;
    }

    public Message makeMessage(boolean makeID, String type) {
        Message ret = new Message(type);
        if (makeID) ret.setID(MessageDistributor.makeID());
        return ret;
    }

}
