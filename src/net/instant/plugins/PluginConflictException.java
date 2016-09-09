package net.instant.plugins;

public class PluginConflictException extends PluginException {

    public PluginConflictException() {
        super();
    }
    public PluginConflictException(String message) {
        super(message);
    }
    public PluginConflictException(Throwable cause) {
        super(cause);
    }
    public PluginConflictException(String message, Throwable cause) {
        super(message, cause);
    }

}
