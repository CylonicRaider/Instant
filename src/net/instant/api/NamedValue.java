package net.instant.api;

/**
 * A generic interface for marking objects with textual names.
 */
public interface NamedValue {
    // A same-named abstract class exists in org.omg.CORBA, but serves
    // somewhat different purposes.

    /**
     * The name of this object.
     */
    String getName();

}
