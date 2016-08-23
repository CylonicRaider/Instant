package net.instant.proto;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.instant.api.Room;
import net.instant.api.RoomGroup;
import net.instant.util.UniqueCounter;
import org.java_websocket.WebSocket;

public class MessageDistributor {

    public class RoomDistributor {

        private final String name;
        private final Set<WebSocket> conns;

        public RoomDistributor(String name) {
            this.name = name;
            conns = new HashSet<WebSocket>();
        }

        public String getName() {
            return name;
        }

        public synchronized void add(WebSocket conn) {
            conns.add(conn);
        }

        public synchronized void remove(WebSocket conn) {
            conns.remove(conn);
        }

        public synchronized void broadcast(ByteBuffer message) {
            for (WebSocket c : conns) {
                c.send(message);
            }
        }
        public synchronized void broadcast(String message) {
            for (WebSocket c : conns) {
                c.send(message);
            }
        }
        public void broadcast(Message message) {
            broadcast(message.makeString());
        }

    }

    private final Map<String, RoomDistributor> rooms;
    private final Map<WebSocket, RoomDistributor> sockets;
    private final Map<WebSocket, String> connids;
    private final Map<String, WebSocket> revconnids;

    public MessageDistributor() {
        rooms = new HashMap<String, RoomDistributor>();
        sockets = new HashMap<WebSocket, RoomDistributor>();
        connids = new HashMap<WebSocket, String>();
        revconnids = new HashMap<String, WebSocket>();
    }

    public synchronized RoomDistributor get(String name) {
        RoomDistributor rd = rooms.get(name);
        if (rd == null) {
            rd = new RoomDistributor(name);
            rooms.put(name, rd);
        }
        return rd;
    }
    public synchronized RoomDistributor get(WebSocket sock) {
        return sockets.get(sock);
    }

    public synchronized String add(String name, WebSocket sock, String id) {
        RoomDistributor d = get(name);
        d.add(sock);
        sockets.put(sock, d);
        connids.put(sock, id);
        revconnids.put(id, sock);
        return id;
    }
    public synchronized String add(String name, WebSocket sock) {
        return add(name, sock, makeID());
    }
    public synchronized void remove(WebSocket sock) {
        RoomDistributor rd = sockets.remove(sock);
        if (rd != null) rd.remove(sock);
        String id = connids.remove(sock);
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

    public synchronized String connectionID(WebSocket sock) {
        return connids.get(sock);
    }
    public synchronized WebSocket connection(String id) {
        return revconnids.get(id);
    }

    public static String makeID() {
        return UniqueCounter.INSTANCE.getString();
    }
    public static UUID makeUUID() {
        return UniqueCounter.INSTANCE.getUUID();
    }

}
