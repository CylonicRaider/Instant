package net.instant.proto;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.instant.api.RequestResponseData;
import net.instant.api.Room;
import net.instant.api.RoomGroup;
import net.instant.util.UniqueCounter;

public class MessageDistributor implements RoomGroup {

    private final Map<String, RoomDistributor> rooms;
    private final Map<RequestResponseData, RoomDistributor> connections;
    private final Map<RequestResponseData, String> connids;
    private final Map<String, RequestResponseData> revconnids;

    public MessageDistributor() {
        rooms = new HashMap<String, RoomDistributor>();
        connections = new HashMap<RequestResponseData, RoomDistributor>();
        connids = new HashMap<RequestResponseData, String>();
        revconnids = new HashMap<String, RequestResponseData>();
    }

    public synchronized RoomDistributor get(String name) {
        RoomDistributor rd = rooms.get(name);
        if (rd == null) {
            rd = new RoomDistributor(this, name);
            rooms.put(name, rd);
        }
        return rd;
    }
    public synchronized RoomDistributor get(RequestResponseData conn) {
        return connections.get(conn);
    }

    public RoomDistributor getRoom(String name) {
        return get(name);
    }
    public RoomDistributor getRoom(RequestResponseData conn) {
        return get(conn);
    }

    public synchronized Set<Room> getActiveRooms() {
        return new HashSet<Room>(rooms.values());
    }

    public synchronized String add(String name, RequestResponseData conn,
                                   String id) {
        RoomDistributor d = get(name);
        d.add(conn);
        connections.put(conn, d);
        connids.put(conn, id);
        revconnids.put(id, conn);
        return id;
    }
    public synchronized String add(String name, RequestResponseData conn) {
        return add(name, conn, makeID());
    }
    public synchronized void remove(RequestResponseData conn) {
        RoomDistributor rd = connections.remove(conn);
        if (rd != null) rd.remove(conn);
        String id = connids.remove(conn);
        if (id != null) revconnids.remove(id);
    }

    public void broadcast(ByteBuffer message) {
        List<RoomDistributor> l;
        synchronized (this) {
            l = new ArrayList<RoomDistributor>(rooms.values());
        }
        for (RoomDistributor d : l) {
            d.broadcast(message);
        }
    }
    public void broadcast(String message) {
        List<RoomDistributor> l;
        synchronized (this) {
            l = new ArrayList<RoomDistributor>(rooms.values());
        }
        for (RoomDistributor d : l) {
            d.broadcast(message);
        }
    }
    public void broadcast(Message message) {
        broadcast(message.makeString());
    }
    public void broadcast(String room, ByteBuffer message) {
        get(room).broadcast(message);
    }
    public void broadcast(String room, String message) {
        get(room).broadcast(message);
    }
    public void broadcast(String room, Message message) {
        broadcast(room, message.makeString());
    }

    public synchronized String connectionID(RequestResponseData conn) {
        return connids.get(conn);
    }
    public synchronized RequestResponseData connection(String id) {
        return revconnids.get(id);
    }

    public static String makeID() {
        return UniqueCounter.INSTANCE.getString();
    }
    public static UUID makeUUID() {
        return UniqueCounter.INSTANCE.getUUID();
    }

}
