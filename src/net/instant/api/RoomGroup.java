package net.instant.api;

import java.util.Set;

/**
 * The entirety of all rooms available as a non-static object.
 */
public interface RoomGroup {

    /**
     * A set of all currently present Room instances.
     * The special no-rooms instance (see getRoom(String)) is not included.
     */
    Set<Room> getActiveRooms();

    /**
     * Get (and possibly create) a room for the given name.
     * null returns the special no-rooms instance.
     */
    Room getRoom(String name);

    /**
     * Get the room the client is connected to, if any.
     * If a client is in no room, null is returned.
     */
    Room getRoom(ClientConnection client);

}
