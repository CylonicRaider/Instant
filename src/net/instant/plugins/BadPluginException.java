package net.instant.plugins;

public class BadPluginException extends PluginException {

    public BadPluginException() {
        super();
    }
    public BadPluginException(String message) {
        super(message);
    }
    public BadPluginException(Throwable cause) {
        super(cause);
    }
    public BadPluginException(String message, Throwable cause) {
        super(message, cause);
    }

}
