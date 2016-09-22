package net.instant.api;

/**
 * Individual per-plugin information which cannot be obtained from the API.
 * An instance of this is passed to plugins' initializer functions.
 */
public interface PluginData {

    /**
     * The name of the plugin being loaded.
     */
    public String getName();

}
