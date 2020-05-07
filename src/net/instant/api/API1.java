package net.instant.api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;
import net.instant.api.parser.ParserFactory;

/**
 * Instant Programmers' Backend Interface revision 1.
 * Where mentioned, the "core" refers the "base" Instant, without plugins.
 */
public interface API1 {

    /**
     * Return the version of this API implementation.
     */
    String getVersion();

    /**
     * Return a fine-grained version indication, or null if not available.
     * This could be, e.g., a VCS commit identifier.
     */
    String getFineVersion();

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
     * NOTE that the "original" location of the file (i.e. the to argument)
     *      is normally accessible as well.
     */
    void addFileAlias(String from, String to);

    /**
     * Alias a regular expression matching paths to a template.
     * Used by the backend to map virtual files from various locations (like
     * /room/welcome/) to an actual file (like /static/room.html).
     * to is a template which is expanded by replacing backslashes followed
     * by (optionally brace-enclosed) group numbers with the corresponding
     * groups as matched by the pattern (where "group 0" is the entire match)
     * and replacing double backslashes with single ones.
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
     *      a single path was deemed too rare to be useful.
     */
    void addContentType(String pattern, String type);

    /**
     * Redirect clients from a path to another.
     * Differently to file aliases, whose results are served as the path the
     * client requested, redirects actually change the URL the client is
     * requesting (or, rather, ask it to). code is the HTTP code to use.
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
     * ID-s are time-based and guaranteed to be unique across all generators
     * (which may be the same instance) for the lifetime of a backend, and
     * probably beyond.
     */
    Counter getCounter();

    /**
     * Obtain the global room group.
     * Can be used to enumerate all rooms without having a reference to some
     * room already present.
     */
    RoomGroup getRooms();

    /**
     * Schedule the given Runnable to be run once or regularly.
     * callback is the Runnable to invoke.
     * delay specifies the time to wait for before the first invocation; it
     * may be zero to denote "immediately", or -1 to denote "parallelizable".
     * All tasks whose delay is not -1 are serialized with respect to each
     * other.
     * period may be -1 to denote "no repetitions", or the amount of
     * milliseconds between subsequent invocations (note that this is
     * independent of how long callback takes to run).
     * Use the return value to cancel execution.
     */
    Future<?> scheduleJob(Runnable callback, long delay, long period);

    /**
     * Return the central job scheduler instance.
     * Can be used to perform e.g. regular cleanup without having to create a
     * dedicated thread. All jobs executed by this instance are serialized
     * with respect to each other. For potentially heavy (and parallel)
     * workloads, use getExecutor().
     */
    ScheduledExecutorService getScheduledExecutor();

    /**
     * Return the central job runner instance.
     * This should be used to offload execution of potentially heavy
     * background tasks.
     */
    ExecutorService getExecutor();

    /**
     * Install an object in the backend console's global namespace.
     * Users of the console will be able to access the object's public
     * attributes and methods via the given name.
     */
    void addConsoleObject(String name, Object obj);

    /**
     * Obtain an entry point into the parser API.
     * The parser API dynamically generates parsers from grammars provided at
     * runtime and provides a framework for mapping the parse trees they
     * produce into Java objects.
     */
    ParserFactory getParserFactory();

    /**
     * Obtain a configuration value.
     * Configuration values are named by hierarchical dot-delimited lowercase
     * strings.
     * The code distinguishes between empty values and the absence of such;
     * a non-null return does not guarantee the string not to be empty.
     * Currently, system properties and environment variables (where the name
     * is converted to uppercase and dots are replaced with underscores) are
     * checked; additional sources may be salvaged in the future.
     */
    String getConfiguration(String name);

    /**
     * Invoke the default plugin initializer.
     * A plugin may wish to both use the declarative features and execute own
     * code upon initialization.
     * Returns the object returned by the default handler.
     */
    Object handleDefault(PluginData data);

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
