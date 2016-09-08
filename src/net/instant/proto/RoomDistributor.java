package net.instant.proto;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.instant.api.MessageContents;
import net.instant.api.RequestResponseData;
import net.instant.api.Room;

public class RoomDistributor implements Room {

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

    public void add(RequestResponseData conn) {
        conns.add(conn);
    }

    public void remove(RequestResponseData conn) {
        conns.remove(conn);
    }

    public void broadcast(ByteBuffer message) {
        synchronized (conns) {
            for (RequestResponseData c : conns) {
                c.getConnection().send(message);
            }
        }
    }
    public void broadcast(String message) {
        synchronized (conns) {
            for (RequestResponseData c : conns) {
                c.getConnection().send(message);
            }
        }
    }
    public void broadcast(MessageContents message) {
        broadcast(message.toString());
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

}
