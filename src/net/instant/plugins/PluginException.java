package net.instant.plugins;

public class PluginException extends Exception {

    public PluginException() {
        super();
    }
    public PluginException(String message) {
        super(message);
    }
    public PluginException(Throwable cause) {
        super(cause);
    }
    public PluginException(String message, Throwable cause) {
        super(message, cause);
    }

}
