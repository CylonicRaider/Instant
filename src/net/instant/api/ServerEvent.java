package net.instant.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A server-sent event.
 * The class provides convenience methods for accessing the attributes of the
 * event, and, most importantly, the toString() method which serializes the
 * event as conforming to the standard. The values are recalled in the order
 * they were inserted.
 * Standard fields are:
 * - id: The ID of the event. If the client reconnects, it can set the
 *   Last-Event-ID field to this to ensure it has not missed events.
 * - event: The type of the event. Directly translated into the type of event
 *   JavaScript clients receive.
 * - data: The event payload. Must be (as everything else) textual.
 * - retry: The new reconnection timeout setting. After the specified
 *   (integral) amount of milliseconds, the client will try to reconnect.
 */
public class ServerEvent {

    /**
     * A string usable as a keep-alive message that does not issue an event,
     * properly serialized.
     */
    public static final String KEEPALIVE = ":\n\n";

    private final Map<String, String> fields;

    /**
     * Construct a ServerEvent wrapping the given map.
     */
    public ServerEvent(Map<String, String> fields) {
        this.fields = fields;
    }
    /**
     * Construct a ServerEvent backed by a new map.
     * The underlying map is guaranteed to retain insertion order.
     */
    public ServerEvent() {
        this(new LinkedHashMap<String, String>());
    }

    /**
     * Return the serialization of this event as it would be sent.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> ent : fields.entrySet()) {
            String key = ent.getKey();
            checkKey(key);
            for (String ln : ent.getValue().split("\n", -1)) {
                sb.append(key);
                sb.append(": ");
                sb.append(ln);
                sb.append('\n');
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * A (new) array of the keys currently held by the instance.
     */
    public String[] keys() {
        Set<String> ks = fields.keySet();
        return ks.toArray(new String[ks.size()]);
    }

    /**
     * The value associated with the given key, or null.
     */
    public String get(String key) {
        return fields.get(key);
    }

    /**
     * Associate the given value with the given key.
     */
    public void put(String key, String value) {
        checkKey(key);
        fields.put(key, value);
    }

    /**
     * Convenience wrapper for multiple put() calls.
     * There must be an even amount of arguments, each consecutive pair of
     * which forms a key-value pair to be inserted.
     * Returns the instance being operated on to facilitate method chaining.
     */
    public ServerEvent update(String... params) {
        int len = params.length;
        if (len % 2 != 0)
            throw new IllegalArgumentException("Bad argument count");
        for (int i = 0; i < len; i += 2)
            put(params[i], params[i + 1]);
        return this;
    }

    /**
     * Remove the given key.
     */
    public String remove(String key) {
        return fields.remove(key);
    }

    /**
     * The underlying collection.
     */
    public Map<String, String> collection() {
        return fields;
    }

    /**
     * Check whether the given key is valid.
     * Raises an IllegalArgumentException is not.
     */
    protected static void checkKey(String k) {
        if (k == null || k.matches("(?s).*(\\s|:)"))
            throw new IllegalArgumentException("Bad SSE key");
    }

    /**
     * A convenience wrapper for constructing a new event, updating it
     * with the given parameters, and returning its string serialization.
     */
    public static String makeString(String... params) {
        return new ServerEvent().update(params).toString();
    }

}
