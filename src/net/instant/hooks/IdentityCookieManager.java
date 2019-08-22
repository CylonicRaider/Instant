package net.instant.hooks;

import java.util.Calendar;
import java.util.UUID;
import net.instant.api.API1;
import net.instant.api.Cookie;
import net.instant.api.Counter;
import net.instant.api.RequestData;
import net.instant.api.ResponseBuilder;
import net.instant.api.Utilities;
import org.json.JSONObject;

public class IdentityCookieManager {

    private static final String K_INSECURE = "instant.cookies.insecure";

    public static final String DEFAULT_COOKIE_NAME = "uid";
    public static final String DATA_KEY_ID = "id";
    public static final String DATA_KEY_UUID = "uuid";

    private final API1 api;
    private final String cookieName;
    private boolean securingCookies;

    public IdentityCookieManager(API1 api, String cookieName) {
        this.api = api;
        this.cookieName = cookieName;
        this.securingCookies = (! Utilities.isTrue(api.getConfiguration(
            K_INSECURE)));
    }
    public IdentityCookieManager(API1 api) {
        this(api, DEFAULT_COOKIE_NAME);
    }

    public String getCookieName() {
        return cookieName;
    }

    public boolean isSecuringCookies() {
        return securingCookies;
    }
    public void setSecuringCookies(boolean s) {
        securingCookies = s;
    }

    public Cookie get(ResponseBuilder resp) {
        return resp.getResponseCookie(getCookieName());
    }

    public Cookie make(RequestData req, ResponseBuilder resp,
                       boolean createNew) {
        Cookie cookie = get(resp);
        if (cookie != null) return cookie;
        cookie = req.getCookie(getCookieName());
        if (! createNew && (cookie == null || cookie.getData() == null)) {
            return null;
        } else if (cookie == null) {
            cookie = resp.makeCookie(cookieName, new JSONObject());
        } else {
            cookie = cookie.copy();
        }
        if (cookie.getData() == null) {
            cookie.setData(new JSONObject());
        }
        init(req, resp, cookie);
        resp.addResponseCookie(cookie);
        return cookie;
    }

    protected void init(RequestData req, ResponseBuilder resp,
                        Cookie cookie) {
        JSONObject data = cookie.getData();
        Counter ctr = api.getCounter();
        long id = ctr.get();
        UUID uuid;
        try {
            uuid = UUID.fromString(data.getString(DATA_KEY_UUID));
        } catch (Exception exc) {
            uuid = ctr.getUUID(id);
        }
        data.put(DATA_KEY_UUID, uuid.toString());
        req.getExtraData().put(DATA_KEY_ID, ctr.getString(id));
        req.getExtraData().put(DATA_KEY_UUID, uuid);
        cookie.updateAttributes("Path", "/", "HttpOnly", null,
            "Expires", Utilities.calendarIn(Calendar.YEAR, 2));
        if (isSecuringCookies()) cookie.put("Secure", null);
    }

}
