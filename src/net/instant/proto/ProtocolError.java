package net.instant.proto;

import net.instant.util.Util;

public class ProtocolError {

    public static final ProtocolError NOT_TEXT =
        new ProtocolError("NOT_TEXT", "Non-textual packet received");
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

    public MessageData makeMessage(Object... detail) {
        Object packedDetail = (detail.length == 0) ? null :
            Util.createJSONObject(detail);
        return new MessageData("error").withData("code", code,
            "message", message, "detail", packedDetail);
    }

}
