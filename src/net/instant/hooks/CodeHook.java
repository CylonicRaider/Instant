package net.instant.hooks;

import java.nio.ByteBuffer;
import net.instant.api.ClientConnection;
import net.instant.api.RequestData;
import net.instant.api.ResponseBuilder;
import net.instant.util.Encodings;

public class CodeHook extends HookAdapter {

    public static final CodeHook NOT_FOUND = new CodeHook(404, "Not Found");

    private final int code;
    private final String message;
    private final ByteBuffer response;

    public CodeHook(int code, String message) {
        this.code = code;
        this.message = message;
        this.response = Encodings.toBytes(code + " " + message);
    }

    public boolean evaluateRequest(RequestData req, ResponseBuilder resp) {
        resp.respond(code, message, response.limit());
        resp.addHeader("Content-Type", "text/plain; charset=utf-8");
        return true;
    }

    public void onOpen(ClientConnection conn) {
        conn.getConnection().send(response);
        conn.getConnection().close();
    }

}
