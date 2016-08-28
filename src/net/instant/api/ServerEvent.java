package net.instant.api;

import java.util.Map;

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
public interface ServerEvent {

    /**
     * A (new) array of the keys currently held by the instance.
     */
    String[] keys();

    /**
     * The value associated with the given key, or null.
     */
    String get(String key);

    /**
     * Associate the given value with the given key.
     */
    void put(String key, String value);

    /**
     * Convenience wrapper for multiple put() calls.
     * There must be an even amount of arguments, each consecutive pair of
     * which forms a key-value pair to be inserted.
     * Returns the instance being operated on to facilitate method chaining.
     */
    ServerEvent update(String... pairs);

    /**
     * Remove the given key.
     */
    String remove(String key);

    /**
     * The underlying collection.
     */
    Map<String, String> collection();

    /**
     * A string usable as a keep-alive message that does not issue an event,
     * properly serialized.
     */
    String keepalive();

}
