package net.instant.api;

/**
 * Information about a client joining or leaving.
 */
public interface PresenceChange {

    /**
     * The new presence status of the client.
     */
    boolean isPresent();

    /**
     * The client that is joining/leaving.
     */
    RequestResponseData getSource();

    /**
     * The room the client is joining/leaving.
     */
    Room getRoom();

    /**
     * Message that will be broadcast to the room to inform about the event.
     * The core does not set the data field because the other ones suffice
     * for it; to transmit additional data, set it (preferably) to a
     * JSONObject if not already done by another plugin.
     */
    MessageContents getMessage();

}
