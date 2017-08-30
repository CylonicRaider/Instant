package net.instant.api;

/**
 * A hook for processing messages in rooms.
 * This only applies to the WebSocket messages handled by the core itself
 * as part of servicing rooms (/room/.../ws) or other API endpoints.
 * Every hook handles messages from every room/endpoint; more fine-grained
 * scoping must be implemented otherwise.
 */
public interface MessageHook {

    /**
     * Respond to a new client connecting.
     * change holds more generic information about the event, while greeting
     * is the message that will be sent to the client as the very first one.
     * To submit additional data to the client alone, amend the data field of
     * greeting.
     * The event cannot be consumed and is handled by all plugins equally.
     * NOTE that the client is not a member of its room (if any) yet; this
     *      method is called before submitting the initial message, which is
     *      sent before adding the client to the room.
     */
    void onConnect(PresenceChange change, MessageContents greeting);

    /**
     * Process a message from a client in a room.
     * message contains the individual message and information about its
     * source and the room it originated from.
     * Should not be confused with RequestHook.onInput(), which can handle
     * "messages" as well.
     * If the return value is true, the message counts as "consumed", and
     * will not be handled further, as would be the case when the return
     * value were false.
     */
    boolean onMessage(Message message);

    /**
     * Respond to a client disconnecting.
     * The event can (as onJoin) not be consumed.
     */
    void onDisconnect(PresenceChange change);

}
