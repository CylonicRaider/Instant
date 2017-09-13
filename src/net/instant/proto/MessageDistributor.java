package net.instant.proto;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.instant.api.ClientConnection;
import net.instant.api.Room;
import net.instant.api.RoomGroup;

public class MessageDistributor implements RoomGroup {

    private final Map<String, RoomDistributor> rooms;
    private final Map<ClientConnection, RoomDistributor> clRooms;
    private final Map<String, ClientConnection> clIndex;

    public MessageDistributor() {
        rooms = new HashMap<String, RoomDistributor>();
        clRooms = new HashMap<ClientConnection, RoomDistributor>();
        clIndex = new HashMap<String, ClientConnection>();
    }

    public Set<Room> getActiveRooms() {
        Set<Room> ret = new HashSet<Room>();
        synchronized (this) {
            for (RoomDistributor d : rooms.values()) {
                if (d.getName() != null) ret.add(d);
            }
        }
        return ret;
    }

    public synchronized RoomDistributor getRoom(String name) {
        RoomDistributor ret = rooms.get(name);
        if (ret == null) {
            ret = new RoomDistributor(this, name);
            rooms.put(name, ret);
        }
        return ret;
    }

    public synchronized RoomDistributor getRoom(ClientConnection client) {
        return clRooms.get(client);
    }

    public synchronized ClientConnection getClient(String id) {
        return clIndex.get(id);
    }

    public synchronized void add(ClientConnection conn,
                                 RoomDistributor room) {
        room.add(conn);
        clRooms.put(conn, room);
        clIndex.put((String) conn.getExtraData().get("id"), conn);
    }
    public synchronized RoomDistributor remove(ClientConnection conn) {
        RoomDistributor r = clRooms.remove(conn);
        if (r != null) r.remove(conn);
        clIndex.remove((String) conn.getExtraData().get("id"));
        return r;
    }

}
