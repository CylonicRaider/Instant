package net.instant.hooks;

import java.nio.ByteBuffer;
import net.instant.api.ClientConnection;
import net.instant.api.RequestData;
import net.instant.api.RequestType;
import net.instant.api.ResponseBuilder;
import net.instant.util.Encodings;

public class CodeHook extends HookAdapter {

    public interface Filter {

        boolean admit(RequestData req);

    }

    public static final CodeHook NOT_FOUND = new CodeHook(
        404, "Not Found", new Filter() {
            public boolean admit(RequestData req) {
                return (req.getRequestType() == RequestType.ERROR &&
                    req.getMethod().equals("GET"));
            }
        });

    public static final CodeHook METHOD_NOT_ALLOWED = new CodeHook(
        405, "Method Not Allowed", new Filter() {
            public boolean admit(RequestData req) {
                return (req.getRequestType() == RequestType.ERROR &&
                    ! req.getMethod().equals("GET"));
            }
        });

    private final int code;
    private final String message;
    private final Filter filter;
    private final ByteBuffer response;

    public CodeHook(int code, String message, Filter filter) {
        this.code = code;
        this.message = message;
        this.filter = filter;
        this.response = Encodings.toBytes(code + " " + message);
    }
    public CodeHook(int code, String message) {
        this(code, message, null);
    }

    public boolean evaluateRequest(RequestData req, ResponseBuilder resp) {
        if (filter != null && ! filter.admit(req)) return false;
        resp.respond(code, message, response.limit());
        resp.addHeader("Content-Type", "text/plain; charset=utf-8");
        return true;
    }

    public void onOpen(ClientConnection conn) {
        synchronized (this) {
            conn.getConnection().send(response);
            response.rewind();
        }
        conn.getConnection().close();
    }

}
