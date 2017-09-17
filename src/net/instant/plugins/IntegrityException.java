package net.instant.plugins;

public class IntegrityException extends PluginException {

    public IntegrityException() {
        super();
    }
    public IntegrityException(String message) {
        super(message);
    }
    public IntegrityException(Throwable cause) {
        super(cause);
    }
    public IntegrityException(String message, Throwable cause) {
        super(message, cause);
    }

}
