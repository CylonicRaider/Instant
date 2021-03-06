package net.instant.api;

import java.io.IOException;

/**
 * A hook for fetching static files.
 */
public interface FileGenerator {

    /**
     * Test for the presence of a static file.
     * Called with the file's path.
     * Should return whether this generator can produce the given file or
     * not, without blocking.
     * Exceptions thrown imply the absence of the file.
     */
    boolean hasFile(String path) throws IOException;

    /**
     * Produce a static file.
     * Assuming that hasFile() has confirmed the existence of the given
     * path, actually fetch the given file, possibly blocking.
     * The method is run in a background thread.
     * An exception thrown will force the core to "bail out" irregularly.
     */
    FileInfo generateFile(String path) throws IOException;

}
