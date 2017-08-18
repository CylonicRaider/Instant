package net.instant.ws;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.instant.util.Encodings;
import net.instant.util.Formats;
import net.instant.util.StringSigner;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.json.JSONObject;

public class CookieHandler {

    public class Cookie extends LinkedHashMap<String, String>
            implements net.instant.api.Cookie {

        private final String name;
        private String value;
        private transient JSONObject data;

        public Cookie(String name, String value) {
            if (! Formats.HTTP_TOKEN.matcher(name).matches())
                throw new IllegalArgumentException("Bad cookie name");
            this.name = name;
            this.value = value;
            this.data = null;
        }
        public Cookie(Cookie other) {
            this(other.getName(), other.getValue());
            putAll(other);
        }

        public Cookie copy() {
            return new Cookie(this);
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
        public void setValue(String v) {
            value = v;
            data = null;
        }

        public JSONObject getData() {
            if (data == null) data = parseCookieContent(getValue());
            return data;
        }
        public void setData(JSONObject data) {
            setValue(encodeCookieContent(data));
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name);
            sb.append('=');
            sb.append(Formats.escapeHttpString(value));
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

    public List<Cookie> get(ClientHandshake request) {
        List<Cookie> ret = new ArrayList<Cookie>();
        Iterator<String> it = request.iterateHttpFields();
        while (it.hasNext()) {
            String name = it.next();
            if (! name.equals("Cookie")) continue;
            String value = request.getFieldValue(name);
            if (value.isEmpty()) continue;
            Map<String, String> values = Formats.parseTokenMap(value);
            if (values == null) continue;
            for (Map.Entry<String, String> ent : values.entrySet()) {
                try {
                    ret.add(new Cookie(ent.getKey(), ent.getValue()));
                } catch (IllegalArgumentException exc) {}
            }
        }
        return ret;
    }

    public void set(ServerHandshakeBuilder response,
                    net.instant.api.Cookie cookie) {
        response.put("Set-Cookie", cookie.toString());
    }
    public void set(ServerHandshakeBuilder response,
                    Iterable<net.instant.api.Cookie> cookies) {
        for (net.instant.api.Cookie c : cookies) set(response, c);
    }

    public Cookie make(String name, String value) {
        return new Cookie(name, value);
    }
    public Cookie make(String name, JSONObject data) {
        return new Cookie(name, encodeCookieContent(data));
    }

    public JSONObject parseCookieContent(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length != 2) return null;
        byte[] data, signature;
        try {
            data = Encodings.fromBase64(parts[0]);
            signature = Encodings.fromBase64(parts[1]);
        } catch (IllegalArgumentException exc) {
            return null;
        }
        synchronized (this) {
            if (signer == null || ! signer.verify(data, signature))
                return null;
        }
        try {
            return new JSONObject(new String(data, "utf-8"));
        } catch (Exception exc) {
            return null;
        }
    }
    public String encodeCookieContent(JSONObject data) {
        byte[] enc, sig;
        try {
            enc = data.toString().getBytes("utf-8");
        } catch (UnsupportedEncodingException exc) {
            throw new RuntimeException(exc);
        }
        synchronized (this) {
            if (signer == null) return null;
            sig = signer.sign(enc);
            if (sig == null) return null;
        }
        return Encodings.toBase64(enc) + "|" + Encodings.toBase64(sig);
    }

}
