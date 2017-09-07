package net.instant.hooks;

import net.instant.api.ClientConnection;
import net.instant.api.RequestData;
import net.instant.api.ResponseBuilder;

public class NotFoundHook extends HookAdapter {

    // Must be ASCII only.
    public static final String NOT_FOUND = "404 Not Found";

    public boolean evaluateRequest(RequestData req, ResponseBuilder resp) {
        resp.respond(404, "Not Found", NOT_FOUND.length());
        resp.addHeader("Content-Type", "text/plain; charset=us-ascii");
        return true;
    }

    public void onOpen(ClientConnection conn) {
        conn.getConnection().send(NOT_FOUND);
        conn.getConnection().close();
    }

}
