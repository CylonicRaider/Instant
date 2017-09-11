package net.instant.api;

import java.util.Set;

/**
 * The entirety of all rooms and room-agnostic services.
 */
public interface RoomGroup {

    /**
     * A set of all currently present Room instances.
     * The special no-rooms instance is not included.
     */
    Set<Room> getActiveRooms();

    /**
     * Get (and possibly create) a room for the given name.
     * If null is given, the special no-rooms instance is returned.
     */
    Room getRoom(String name);

    /**
     * Get the room the client is connected to, if any.
     * If the client is in no room, the special no-rooms instance is
     * returned.
     */
    Room getRoom(ClientConnection client);

    /**
     * Return the client with the given ID.
     */
    ClientConnection getClient(String id);

}
