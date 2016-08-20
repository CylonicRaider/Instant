package net.instant.api;

/**
 * A hook for fetching static files.
 */
public interface FileGenerator {

    /**
     * Produce a static file or bail out.
     * Called with the file's path (without a query string, which static
     * files should be independent of).
     * Should return either a FileInfo instance or null if no such file
     * was found.
     */
    FileInfo generateFile(String path);

}
