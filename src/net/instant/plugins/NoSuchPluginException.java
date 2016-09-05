package net.instant.plugins;

public class NoSuchPluginException extends Exception {

    public NoSuchPluginException() {
        super();
    }
    public NoSuchPluginException(String message) {
        super(message);
    }
    public NoSuchPluginException(Throwable cause) {
        super(cause);
    }
    public NoSuchPluginException(String message, Throwable cause) {
        super(message, cause);
    }

}
