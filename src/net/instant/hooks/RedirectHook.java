package net.instant.hooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.instant.api.RequestType;
import net.instant.info.RequestInfo;
import net.instant.util.DefaultStringMatcher;
import net.instant.util.StringMatcher;
import net.instant.ws.Draft_Raw;
import net.instant.ws.InstantWebSocketServer;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;

public class RedirectHook extends HookAdapter {

    public enum RedirectType {

        MOVED(301, "Moved Permanently"),
        FOUND(302, "Found"),
        SEE_OTHER(303, "See Other"),
        TEMPORARY(307, "Temporary Redirect"),
        PERMANENT(308, "Permanent Redirect");

        private static final Map<Integer, RedirectType> BY_CODE;

        static {
            BY_CODE = new HashMap<Integer, RedirectType>();
            for (RedirectType t : values())
                BY_CODE.put(t.getCode(), t);
        }

        private final int code;
        private final String message;

        private RedirectType(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }
        public String getMessage() {
            return message;
        }

        public static RedirectType byCode(int code) {
            return BY_CODE.get(code);
        }

    }

    public interface Redirect extends StringMatcher {

        RedirectType getRedirectType();

    }

    public static class DefaultRedirect extends DefaultStringMatcher
            implements Redirect {

        private final RedirectType type;

        public DefaultRedirect(Pattern pat, String repl, boolean dyn,
                               RedirectType type) {
            super(pat, repl, dyn);
            this.type = type;
        }
        public DefaultRedirect(Pattern pat, String repl, RedirectType type) {
            super(pat, repl);
            this.type = type;
        }
        public DefaultRedirect(String pat, String repl, RedirectType type) {
            super(pat, repl);
            this.type = type;
        }

        public RedirectType getRedirectType() {
            return type;
        }

    }

    private final List<Redirect> redirects;

    public RedirectHook() {
        redirects = new ArrayList<Redirect>();
    }

    public void redirect(Redirect r) {
        redirects.add(r);
    }
    public void redirect(Pattern pat, String repl, int code) {
        redirect(new DefaultRedirect(pat, repl, RedirectType.byCode(code)));
    }
    public void redirect(String pat, String repl, int code) {
        redirect(new DefaultRedirect(pat, repl, RedirectType.byCode(code)));
    }

    public boolean allowUnassigned() {
        return false;
    }

    public void postProcessRequest(InstantWebSocketServer parent,
                                   RequestInfo info,
                                   Handshakedata eff_resp) {
        if (info.getRequestType() != RequestType.HTTP) return;
        if (! "GET".equals(info.getBase().getMethod())) return;
        String original = info.getBase().getURL();
        for (Redirect r : redirects) {
            String newURL = r.match(original);
            if (newURL == null) continue;
            RedirectType tp = r.getRedirectType();
            info.respond(tp.getCode(), tp.getMessage(), -1);
            info.putHeader("Location", newURL);
            parent.assign(info, this);
            break;
        }
    }

    public void onOpen(RequestInfo info, ClientHandshake handshake) {
        info.getConnection().close();
    }

}
