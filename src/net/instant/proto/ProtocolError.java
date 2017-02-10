package net.instant.proto;

public class ProtocolError {

    public static final ProtocolError INVALID_JSON =
        new ProtocolError("INVALID_JSON", "Invalid JSON");
    public static final ProtocolError INVALID_TYPE =
        new ProtocolError("INVALID_TYPE", "Invalid message type");
    public static final ProtocolError NO_PARTICIPANT =
        new ProtocolError("NO_PARTICIPANT", "No such participant");

    private final String code;
    private final String message;

    public ProtocolError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }
    public String getMessage() {
        return message;
    }

    public Message makeMessage(Object detail) {
        return new Message("error").makeData("code", code,
            "message", message, "detail", detail);
    }
    public Message makeMessage() {
        return new Message("error").makeData("code", code,
            "message", message);
    }

}
