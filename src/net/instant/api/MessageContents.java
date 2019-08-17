package net.instant.api;

import org.json.JSONObject;
import org.json.JSONString;

/**
 * The contents of a well-formed Instant message.
 */
public class MessageContents implements JSONString {

    private String id;
    private Object sequence;
    private String type;
    private String from;
    private String to;
    private Object data;
    private long timestamp;

    /**
     * Copy constructor.
     */
    public MessageContents(MessageContents other) {
        id = other.getID();
        sequence = other.getSequence();
        type = other.getType();
        from = other.getFrom();
        to = other.getTo();
        data = other.getData();
        timestamp = other.getTimestamp();
    }

    /**
     * Initialize the new instance's fields from the given object.
     * The timestamp, unless given by source, is initialized to the current
     * time. All other fields default to null.
     */
    public MessageContents(JSONObject source) {
        id = source.optString("id", null);
        sequence = source.opt("seq");
        type = source.optString("type", null);
        from = source.optString("from", null);
        to = source.optString("to", null);
        data = source.opt("data");
        timestamp = source.optLong("timestamp", System.currentTimeMillis());
    }

    /**
     * Create a new MessageContents with the given type.
     * The timestamp is set to the current time; all other fields default to
     * null.
     */
    public MessageContents(String type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Default constructor.
     * This sets the timestamp to the current time; all other fields are set
     * to null.
     */
    public MessageContents() {
        this((String) null);
    }

    /**
     * Return the JSON representation of this instance as a String or
     * JSONObject.
     */
    public String toString() {
        return toJSONString();
    }
    public String toJSONString() {
        return toJSONObject().toString();
    }
    public JSONObject toJSONObject() {
        return Utilities.createJSONObject("id", id, "seq", sequence,
            "type", type, "from", from, "to", to, "data", data,
            "timestamp", timestamp);
    }

    /**
     * The message ID.
     * Filled in by the core; should normally not be changed.
     * Messages as sent by clients do not have ID-s; messages sent back to
     * clients do neither; messages passed on to other clients do have them.
     */
    public String getID() {
        return id;
    }
    public void setID(String id) {
        this.id = id;
    }
    public MessageContents id(String id) {
        setID(id);
        return this;
    }

    /**
     * The client-assigned sequence identifier.
     * Should be treated like an opaque value and passed through to responses
     * to the message.
     */
    public Object getSequence() {
        return sequence;
    }
    public void setSequence(Object seq) {
        this.sequence = seq;
    }
    public MessageContents sequence(Object seq) {
        setSequence(seq);
        return this;
    }

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
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public MessageContents type(String type) {
        setType(type);
        return this;
    }

    /**
     * The message's source.
     * Should be a client ID as assigned by the core if the message
     * originated from a client, or not present for messages emitted by the
     * backend itself.
     */
    public String getFrom() {
        return from;
    }
    public void setFrom(String from) {
        this.from = from;
    }
    public MessageContents from(String from) {
        setFrom(from);
        return this;
    }

    /**
     * The message's destination.
     * If the message is directed to a client specifically (such as a unicast
     * message), this is the ID of the destination.
     */
    public String getTo() {
        return to;
    }
    public void setTo(String to) {
        this.to = to;
    }
    public MessageContents to(String to) {
        setTo(to);
        return this;
    }

    /**
     * The message's payload.
     * Arbitrary data whose interpretation depends on the message type.
     */
    public Object getData() {
        return data;
    }
    public void setData(Object data) {
        this.data = data;
    }
    public MessageContents data(Object data) {
        setData(data);
        return this;
    }

    /**
     * Populate the payload with the given key/value pairs.
     * pairs must have an even number of entries, with the first entry of
     * each pair being a String key, and the second an arbitrary object.
     * If the payload is a JSONObject, it is (recursively) amended with
     * the given pairs, otherwise (in particular if it is null), it is
     * replaced.
     */
    public void updateData(Object... params) {
        if (data instanceof JSONObject) {
            Utilities.mergeJSONObjects((JSONObject) data,
                                       Utilities.createJSONObject(params));
        } else {
            setData(Utilities.createJSONObject(params));
        }
    }
    public MessageContents withData(Object... params) {
        updateData(params);
        return this;
    }

    /**
     * Message UNIX timestamp.
     * Filled in by the core; should normally not be changed.
     */
    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long ts) {
        this.timestamp = ts;
    }
    public MessageContents timestamp(long ts) {
        setTimestamp(ts);
        return this;
    }

}
