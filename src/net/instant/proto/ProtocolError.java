package net.instant.proto;

public enum ProtocolError {

    INVALID_JSON(1, "Invalid JSON"),
    INVALID_TYPE(2, "Invalid message type"),
    NO_SUCH_PARTICIPANT(3, "No such participant");

    private final int code;
    private final String message;

    private ProtocolError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }
    public String getMessage() {
        return message;
    }

}
