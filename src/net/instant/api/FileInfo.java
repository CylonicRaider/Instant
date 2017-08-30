package net.instant.api;

import java.nio.ByteBuffer;

/**
 * Information about a static file and its contents.
 * The methods marked as "assumed to be constant" will not be called
 * repeatedly by the core; cached values will be used instead (so that
 * the methods here can perform more-or-less expensive computation).
 */
public interface FileInfo {

    /**
     * The name of the file.
     * Used for caching purposes. Assumed to be constant.
     */
    String getName();

    /**
     * The (binary) content of the file.
     * If the file is textual, a UTF-8 encoding is silently assumed.
     * May be read-only. The reference is assumed to be constant.
     */
    ByteBuffer getData();

    /**
     * The creation date of the (cached) file as a UNIX timestamp.
     * Assumed to be constant.
     */
    long getCreated();

    /**
     * Whether this information is still valid.
     * If the underlying resource changed for which reasons ever, only this
     * method's return value needs to change. The core will reload the file
     * when the need arises.
     */
    boolean isValid();

}
