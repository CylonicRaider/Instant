package net.instant.hooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.instant.api.ClientConnection;
import net.instant.api.RequestData;
import net.instant.api.ResponseBuilder;
import net.instant.util.stringmatch.DefaultStringMatcher;
import net.instant.util.stringmatch.StringMatcher;

public class RedirectHook extends HookAdapter {

    public enum RedirectType {

        /* This and all future requests shall continue to the given new
         * location. */
        MOVED(301, "Moved Permanently"),
        /* Continue with a GET to the given location (or repeat the request
         * to the given new location [HTTP/1.0]). */
        FOUND(302, "Found"),
        /* Continue with a GET of the given location. May be used to redirect
         * from HTML form submission endpoints. */
        SEE_OTHER(303, "See Other"),
        /* Repeat the request with the same method to the given location. */
        TEMPORARY(307, "Temporary Redirect"),
        /* This and all future requests shall continue to the given new
         * location, and the request method may not change. */
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

        public static RedirectType forCode(int code) {
            return BY_CODE.get(code);
        }

    }

    public interface Redirect extends StringMatcher {

        RedirectType getRedirectType();

    }

    public static class DefaultRedirect extends DefaultStringMatcher
            implements Redirect {

        private final RedirectType type;

        public DefaultRedirect(Pattern p, String r, boolean d,
                               RedirectType t) {
            super(p, r, d);
            type = t;
        }

        public RedirectType getRedirectType() {
            return type;
        }

    }

    private final List<Redirect> redirects;

    public RedirectHook() {
        redirects = new ArrayList<Redirect>();
    }

    public Redirect add(Redirect r) {
        redirects.add(r);
        return r;
    }
    public void remove(Redirect r) {
        redirects.remove(r);
    }

    public Redirect add(Pattern p, String r, int c) {
        return add(new DefaultRedirect(p, r, true,
                                       RedirectType.forCode(c)));
    }
    public Redirect add(String p, String r, int c) {
        return add(new DefaultRedirect(Pattern.compile(Pattern.quote(p)),
                                       r, false, RedirectType.forCode(c)));
    }

    public boolean evaluateRequest(RequestData req, ResponseBuilder resp) {
        for (Redirect r : redirects) {
            String l = r.match(req.getPath());
            if (l == null) continue;
            RedirectType t = r.getRedirectType();
            resp.respond(t.getCode(), t.getMessage(), -1);
            resp.addHeader("Location", l);
            return true;
        }
        return false;
    }

    public void onOpen(ClientConnection conn) {
        conn.getConnection().close();
    }

}
