package net.instant.hooks;

import net.instant.api.RequestData;
import net.instant.api.RequestHook;
import net.instant.api.RequestType;
import net.instant.api.ResponseBuilder;
import net.instant.util.ListStringMatcher;

public abstract class WebSocketHook implements RequestHook {

    private final ListStringMatcher whitelist;

    public WebSocketHook() {
        whitelist = new ListStringMatcher();
    }

    public ListStringMatcher getWhitelist() {
        return whitelist;
    }

    public boolean evaluateRequest(RequestData req, ResponseBuilder resp) {
        // Let the WS library create request/response.
        if (req.getRequestType() != RequestType.WS) return false;
        String tag = whitelist.match(req.getPath());
        if (tag == null) return false;
        resp.respond(101, "Switching Protocols", -1);
        resp.addHeader("Content-Type", "application/x-websocket");
        return evaluateRequestInner(req, resp, tag);
    }

    protected abstract boolean evaluateRequestInner(RequestData req,
        ResponseBuilder resp, String tag);

}
