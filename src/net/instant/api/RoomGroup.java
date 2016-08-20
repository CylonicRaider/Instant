package net.instant.api;

import java.util.Set;

/**
 * The entirety of all rooms available as a non-static object.
 */
public interface RoomGroup {

    /**
     * A set of all currently present Room instances.
     */
    Set<Room> getActiveRooms();

    /**
     * Get (and possibly create) a room for the given name.
     */
    Room getRoom(String name);

}
