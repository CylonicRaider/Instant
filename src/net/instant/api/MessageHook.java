package net.instant.api;

/**
 * A hook for processing messages in rooms.
 * This only applies to the WebSocket messages handled by the core itself
 * as part of servicing rooms (/room/.../).
 */
public interface MessageHook {

    /**
     * Process a message from a client in a room.
     * message contains the individual message and information about its
     * source and the room it originated from.
     * Should not be confused with RequestHook.onInput(), which can handle
     * "messages" as well.
     * If the return value is true, the message counts as "consumed", and
     * will not be handled further, as is the case when the return value is
     * false.
     */
    boolean onMessage(Message message);

}
