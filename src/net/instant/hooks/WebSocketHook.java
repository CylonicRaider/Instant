package net.instant.hooks;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import net.instant.api.RequestData;
import net.instant.api.RequestType;
import net.instant.api.ResponseBuilder;
import net.instant.util.Util;

public class WebSocketHook extends HookAdapter {

    private final List<Pattern> whitelist;

    public WebSocketHook() {
        whitelist = new LinkedList<Pattern>();
    }

    public List<Pattern> getWhitelist() {
        return whitelist;
    }
    public void whitelist(Pattern path) {
        whitelist.add(path);
    }
    public void unwhitelist(Pattern path) {
        whitelist.remove(path);
    }

    public boolean evaluateRequest(RequestData req, ResponseBuilder resp) {
        // Let the WS library create request/response.
        if (req.getRequestType() != RequestType.WS ||
                ! Util.matchWhitelist(
                    req.getPath().replaceFirst("\\?.*", ""),
                    whitelist))
            return false;
        resp.respond(101, "Switching Protocols", -1);
        return true;
    }

}
