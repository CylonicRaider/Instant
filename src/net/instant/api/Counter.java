package net.instant.api;

import java.util.UUID;

/**
 * A unique ID generator.
 * To reduce collisions, the core provides a central instance for all
 * needs. Do not use wastefully.
 */
public interface Counter {

    /**
     * Obtain a unique 64-bit ID.
     * ID-s increase monotonically, unless the system clock changes abruptly.
     */
    long get();

    /**
     * Obtain the string form of either a new ID or the given one.
     * The core's implementation returns a sixteen-digit uppercase
     * hexadecimal representation of the ID.
     */
    String getString();
    String getString(long id);

    /**
     * Obtain the UUID form of a new ID or the given one.
     * The core embeds the ID into the timestamp (and, against the standard,
     * the "clock sequence" field) of a Type-1 UUID, using a random (but
     * constant for the lifetime of a backend) node identifier field to
     * increase cross-instance uniqueness.
     */
    UUID getUUID();
    UUID getUUID(long id);

}
