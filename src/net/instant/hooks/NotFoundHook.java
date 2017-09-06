package net.instant.hooks;

import net.instant.api.ClientConnection;
import net.instant.api.RequestData;
import net.instant.api.ResponseBuilder;

public class NotFoundHook extends HookAdapter {

    public boolean evaluateRequest(RequestData req, ResponseBuilder resp) {
        resp.respond(404, "Not Found", -1);
        return true;
    }

    public void onOpen(ClientConnection conn) {
        conn.getConnection().close();
    }

}
