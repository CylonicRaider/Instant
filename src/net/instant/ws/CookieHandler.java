package net.instant.ws;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.instant.api.Cookie;
import net.instant.util.Encodings;
import net.instant.util.Formats;
import net.instant.util.StringSigner;
import net.instant.util.Util;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.json.JSONObject;

public class CookieHandler {

    /* Accessing enclosing class for string signing. */
    public class DefaultCookie extends LinkedHashMap<String, String>
            implements Cookie {

        private final String name;
        private String value;
        private JSONObject data;

        public DefaultCookie(String name, String value) {
            if (! Formats.HTTP_TOKEN.matcher(name).matches())
                throw new IllegalArgumentException("Bad cookie name");
            this.name = name;
            this.data = parseCookieContent(value);
            this.value = (data == null) ? value : null;
        }
        public DefaultCookie(Cookie other) {
            this(other.getName(), other.getValue());
            putAll(other);
        }

        public DefaultCookie copy() {
            return new DefaultCookie(this);
        }

        public CookieHandler getParent() {
            return CookieHandler.this;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            if (data != null) {
                return formatCookieContent(data);
            } else {
                return value;
            }
        }
        public void setValue(String v) {
            data = parseCookieContent(v);
            value = (data == null) ? v : null;
        }

        public String getAttribute(String key) {
            return get(key);
        }
        public String setAttribute(String key, Object value) {
            String stringValue;
            if (value == null) {
                stringValue = null;
            } else if (value instanceof String) {
                stringValue = (String) value;
            } else if (value instanceof Number) {
                stringValue = value.toString();
            } else if (value instanceof Calendar) {
                stringValue = Formats.formatHttpTime((Calendar) value);
            } else if (value instanceof Date) {
                stringValue = Formats.formatHttpTime((Date) value);
            } else {
                throw new ClassCastException("Cannot convert cookie " +
                    "attribute value " + value + " (for key " + key + ")");
            }
            return put(key, stringValue);
        }
        public String removeAttribute(String key) {
            return remove(key);
        }

        public void updateAttributes(Object... pairs) {
            if (pairs.length % 2 != 0)
                throw new IllegalArgumentException("Invalid argument " +
                    "amount for withAttributes()");
            for (int i = 0; i < pairs.length; i += 2) {
                if (! (pairs[i] instanceof String))
                    throw new IllegalArgumentException("Invalid argument " +
                        pairs[i] + " for updateAttributes(): Not a string");
                setAttribute((String) pairs[i], pairs[i + 1]);
            }
        }
        public DefaultCookie withAttributes(Object... pairs) {
            updateAttributes(pairs);
            return this;
        }

        public JSONObject getData() {
            return data;
        }
        public void setData(JSONObject d) {
            data = d;
            value = null;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name);
            sb.append('=');
            sb.append(Formats.escapeHttpString(getValue()));
            for (Map.Entry<String, String> ent : entrySet()) {
                sb.append("; ");
                sb.append(ent.getKey());
                if (ent.getValue() != null) {
                    sb.append('=');
                    sb.append(ent.getValue());
                }
            }
            return sb.toString();
        }

    }

    private StringSigner signer;

    public CookieHandler(StringSigner signer) {
        this.signer = signer;
    }
    public CookieHandler() {
        this(null);
    }

    public synchronized StringSigner getSigner() {
        return signer;
    }
    public synchronized void setSigner(StringSigner s) {
        signer = s;
    }

    public List<Cookie> extractCookies(ClientHandshake request) {
        return extractCookies(Collections.singletonList(
            request.getFieldValue("Cookie")));
    }
    public List<Cookie> extractCookies(Iterable<String> values) {
        List<Cookie> ret = new ArrayList<Cookie>();
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            Map<String, String> cookies = Formats.parseTokenMap(value);
            if (cookies == null) continue;
            for (Map.Entry<String, String> ent : cookies.entrySet()) {
                try {
                    ret.add(makeCookie(ent.getKey(), ent.getValue()));
                } catch (IllegalArgumentException exc) {}
            }
        }
        return ret;
    }

    public void setCookie(ServerHandshakeBuilder response,
                          net.instant.api.Cookie cookie) {
        response.put("Set-Cookie", cookie.toString());
    }
    public void setCookies(ServerHandshakeBuilder response,
                           Iterable<net.instant.api.Cookie> cookies) {
        for (net.instant.api.Cookie c : cookies) setCookie(response, c);
    }

    public Cookie makeCookie(String name, String value) {
        return new DefaultCookie(name, value);
    }
    public Cookie makeCookie(String name, JSONObject data) {
        return makeCookie(name, formatCookieContent(data));
    }

    public JSONObject parseCookieContent(String value) {
        if (value.isEmpty()) return null;
        StringSigner signer;
        synchronized (this) {
            signer = this.signer;
        }
        String[] parts = value.split("\\|", -1);
        if (parts.length == 1) {
            if (signer != null) return null;
            try {
                String decText = new String(Encodings.fromBase64(value),
                                            "utf-8");
                Object liveValue = Util.parseOneJSONValue(decText);
                // This deliberately may throw a ClassCastException.
                return (JSONObject) liveValue;
            } catch (Exception exc) {
                return null;
            }
        } else if (parts.length != 2) {
            return null;
        }
        byte[] data, signature;
        try {
            data = Encodings.fromBase64(parts[0]);
            signature = Encodings.fromBase64(parts[1]);
        } catch (IllegalArgumentException exc) {
            return null;
        }
        if (signer == null || ! signer.verify(data, signature))
            return null;
        try {
            String decData = new String(data, "utf-8");
            Object liveValue = Util.parseOneJSONValue(decData);
            // Again, we deliberately allow ClassCastException-s.
            return (JSONObject) liveValue;
        } catch (Exception exc) {
            return null;
        }
    }
    public String formatCookieContent(JSONObject data) {
        byte[] enc, sig;
        try {
            enc = data.toString().getBytes("utf-8");
        } catch (UnsupportedEncodingException exc) {
            throw new RuntimeException(exc);
        }
        synchronized (this) {
            if (signer == null) return Encodings.toBase64(enc);
            sig = signer.sign(enc);
            if (sig == null) return null;
        }
        return Encodings.toBase64(enc) + "|" + Encodings.toBase64(sig);
    }

}
