package net.instant.ws;

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
        Cookie ret = resp.getResponseCookie(getCookieName());
        if (ret == null || ret.getData() == null) ret = null;
        return ret;
    }

    public Cookie make(RequestData req, ResponseBuilder resp,
                       ResponseBuilder.IdentMode mode) {
        Cookie cookie = get(resp);
        if (mode == ResponseBuilder.IdentMode.OPTIONAL) {
            return cookie;
        }
        if (cookie == null) {
            Cookie origCookie = req.getCookie(getCookieName());
            cookie = (origCookie != null) ? origCookie.copy() :
                resp.makeCookie(getCookieName(), "");
            resp.addResponseCookie(cookie);
        }
        if (cookie.getData() == null) {
            cookie.setData(new JSONObject());
        }
        init(req, resp, mode, cookie);
        return cookie;
    }

    protected void init(RequestData req, ResponseBuilder resp,
                        ResponseBuilder.IdentMode mode, Cookie cookie) {
        JSONObject data = cookie.getData();
        Counter ctr = api.getCounter();
        long id = -1;
        UUID uuid;
        try {
            uuid = UUID.fromString(data.getString(DATA_KEY_UUID));
        } catch (Exception exc) {
            if (id == -1) id = ctr.get();
            uuid = ctr.getUUID(id);
        }
        data.put(DATA_KEY_UUID, uuid.toString());
        req.getExtraData().put(DATA_KEY_UUID, uuid);
        if (mode == ResponseBuilder.IdentMode.INDIVIDUAL) {
            if (id == -1) id = ctr.get();
            req.getExtraData().put(DATA_KEY_ID, ctr.getString(id));
        }
        cookie.updateAttributes("Path", "/", "HttpOnly", null,
            "Expires", Utilities.calendarIn(Calendar.YEAR, 2));
        if (isSecuringCookies()) cookie.put("Secure", null);
    }

}
