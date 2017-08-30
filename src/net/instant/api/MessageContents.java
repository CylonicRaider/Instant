package net.instant.api;

/**
 * The contents of a well-formatted Instant message.
 * The toString() method returns an appropriate JSON serialization which can
 * be sent to clients.
 */
public interface MessageContents {

    /**
     * The message ID.
     * Filled in by the core; should normally not be changed.
     * Messages as sent by clients do not have ID-s; messages sent back to
     * clients do neither; messages passed on to other clients do have them.
     */
    String getID();
    void setID(String id);

    /**
     * The client-assigned sequence identifier.
     * Should be treated like an opaque value and passed through to responses
     * to the message.
     */
    Object getSequence();
    void setSequence(Object seq);

    /**
     * The message type.
     * Generally, messages passed through to other clients should retain the
     * message type (and the wording should be chosen to facilitate that),
     * unless change is semantically warranted; e.g., "unicast" and
     * "broadcast" messages are nearly fully retransmitted by the core, but a
     * "ping" message is responded to with a "pong".
     * Message types should be short alphanumeric lowercase strings with
     * dashes as word separators.
     */
    String getType();
    void setType(String type);

    /**
     * The message's source.
     * Should be a client ID as assigned by the core if the message
     * originated from a client, or not present for messages emitted by the
     * backend itself.
     */
    String getFrom();
    void setFrom(String from);

    /**
     * The message's destination.
     * If the message is directed to a client specifically (such as a unicast
     * message), this is the ID of the destination.
     */
    String getTo();
    void setTo(String to);

    /**
     * The message's payload.
     * Arbitrary data whose interpretation depends on the message type.
     */
    Object getData();
    void setData(Object data);

    /**
     * Message UNIX timestamp.
     * Filled in by the core; should normally not be changed.
     */
    long getTimestamp();
    void setTimestamp(long ts);

}
