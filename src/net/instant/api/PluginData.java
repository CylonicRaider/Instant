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
     * An auxiliary attribute of the plugin.
     */
    String getAttribute(String attr);

}
