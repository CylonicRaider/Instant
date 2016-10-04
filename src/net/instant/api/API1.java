package net.instant.api;

import java.util.regex.Pattern;

/**
 * Instant Programmers' Backend Interface revision 1.
 * The "core" refers the "base" Instant, without plugins.
 */
public interface API1 {

    /**
     * Add a hook to potentially handle requests.
     * The hook can intercept HTTP requests and handle them, or let others
     * do so. If no hook can handle the request, the core tries own handlers,
     * down to a catch-all "404 not found" page deliverer.
     */
    void addRequestHook(RequestHook hook);

    /**
     * Add a file generator.
     * The generator is asked by the core's static file delivery system to
     * produce a file which is then sent and internalized. If the file is
     * neither static nor "mostly static" (i.e. updating rarely and
     * benefitting from public caching), usage of a request hook should be
     * considered.
     */
    void addFileGenerator(FileGenerator gen);

    /**
     * Alias a single file path to another.
     * Used by the core to provide singular paths like /favicon.ico.
     * NOTE that the original location of the file is accessible as well.
     */
    void addFileAlias(String from, String to);

    /**
     * Alias a regular expression of paths to a template.
     * Used by the backend to map virtual files from various locations (like
     * /room/welcome/) to an actual file (like /static/room.html).
     * to is a template which is expanded by replacing backslashes followed
     * by group numbers with the corresponding groups as matched by the
     * pattern (where "group 0" is the entire match) and replacing double
     * backslashes with single ones.
     */
    void addFileAlias(Pattern from, String to);

    /**
     * Register a content-type to be sent by static files.
     * The type is determined using the file path; the given pattern is
     * tested to match it, if it matches, the given content type is used.
     * Since the patterns are tested against the entire path, most patterns
     * will be like ".*\\.txt".
     * NOTE that differently to file aliases and redirects, pattern is always
     *      a regular expression pattern, since assigning a content type to
     *      a single path is deemed too rare to be useful.
     */
    void addContentType(String pattern, String type);

    /**
     * Redirect clients from a path to another.
     * Differently from file aliases, whose results are served as the path
     * the client requested, redirects actually change the URL the client
     * is requesting. code is the HTTP code to use.
     */
    void addRedirect(String from, String to, int code);

    /**
     * Redirect clients from paths defined by a regex to a template.
     * See notes for addRedirect(String, String, int) and
     * addFileAlias(Pattern, String).
     */
    void addRedirect(Pattern from, String to, int code);

    /**
     * Add a hook for incoming WebSocket messages in rooms.
     * The hook can process a message on its own or allow others (or the
     * core) to do so.
     * Not to be confused with RequestHook (which can intercept "messages"
     * as well).
     */
    void addMessageHook(MessageHook hook);

    /**
     * Add a synthetic static file.
     * This can be used instead of addFileGenerator() for convenience's sake.
     */
    void addSyntheticFile(String name, String content);

    /**
     * Add some JavaScript code to /static/site.js.
     * This can be used to bootstrap frontend-side plugins.
     */
    void addSiteCode(String code);

    /**
     * Obtain a unique ID generator.
     * ID-s are time-based and guaranteed to be unique for the lifetime of
     * a backend.
     */
    Counter getCounter();

    /**
     * Obtain a configuration value.
     * Configuration values are hierarchical dot-delimited lowercase strings.
     * The code distinguishes between empty values and the absence of such;
     * a non-null return does not guarantee the string not to be empty.
     * Currently, system properties and environment variables (where the name
     * is converted to uppercase and dots are replaced with underscores) are
     * checked; additional sources may be salvaged in the future.
     */
    String getConfiguration(String name);

    /**
     * Get the object returned by a plugin's intializer method.
     * Returns null if the method returns null or has a void return type,
     * throws an IllegalArgumentException if there is no plugin referred to
     * by name, or an IllegalStateException if the plugin has not been
     * loaded yet.
     */
    Object getPluginData(String name) throws IllegalArgumentException,
        IllegalStateException;

}
