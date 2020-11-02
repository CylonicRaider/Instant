package net.instant.hooks;

import net.instant.api.ClientConnection;
import net.instant.api.RequestData;
import net.instant.api.RequestType;
import net.instant.api.ResponseBuilder;
import net.instant.util.Encodings;
import net.instant.util.Util;

public class AuthHook extends HookAdapter {

    private String path;

    public String getPath() {
        return path;
    }

    public void setPath(String p) {
        path = p;
    }

    public boolean evaluateRequest(RequestData req, ResponseBuilder resp) {
        if (req.getRequestType() != RequestType.HTTP || path == null ||
                ! path.equals(req.getPath()))
            return false;
        String magic = createMagicCookie();
        resp.respond(200, "OK", magic.length());
        resp.addHeader("Content-Type", "text/plain; charset=utf-8");
        resp.addHeader("Content-Length", Integer.toString(magic.length()));
        resp.addHeader("X-Magic-Cookie", '"' + magic + '"');
        resp.identify(ResponseBuilder.IdentMode.ALWAYS);
        req.getPrivateData().put("magic-cookie", magic);
        return true;
    }

    public void onOpen(ClientConnection conn) {
        String magic = (String) conn.getPrivateData().get("magic-cookie");
        conn.getConnection().send(magic);
        conn.getConnection().close();
    }

    public static String createMagicCookie() {
        return Encodings.toBase64(Util.getRandomness(24));
    }

}
