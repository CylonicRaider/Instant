package net.instant.api;

import java.util.Set;

/**
 * Individual per-plugin information which cannot be obtained from the API.
 * An instance of this is passed to plugins' initializer functions.
 */
public interface PluginData {

    /**
     * The name of the plugin being loaded.
     */
    String getName();

    /**
     * Names of plugins this one depends on.
     */
    Set<String> getDependencies();

    /**
     * An auxillary attribute of the plugin.
     */
    String getAttribute(String attr);

    /**
     * Invoke the default plugin initializer.
     * A plugin may wish to use declarative features, but yet execute own
     * code upon initialization.
     * Returns the object returned by the default handler.
     */
    Object handleDefault();

}
