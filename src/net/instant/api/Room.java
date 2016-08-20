package net.instant.api;

import java.util.Set;

/**
 * Representation of a chat room.
 * Instances may be garbage-collected when there are no clients connected to
 * a room.
 */
public interface Room {

    /**
     * The name of the room.
     */
    String getName();

    /**
     * All clients currently connected to the room.
     */
    Set<RequestResponseData> getClients();

    /**
     * The (global) group the room belongs to.
     */
    RoomGroup getGroup();

}
