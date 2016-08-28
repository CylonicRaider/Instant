package net.instant.ws;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.instant.api.ServerEvent;

/**
 * A single server-sent event.
 */
public class ServerEventImpl implements ServerEvent {

    private final Map<String, String> fields;

    public ServerEventImpl(Map<String, String> fields) {
        this.fields = fields;
    }
    public ServerEventImpl() {
        this(new LinkedHashMap<String, String>());
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> ent : fields.entrySet()) {
            String key = ent.getKey();
            checkKey(key);
            for (String ln : ent.getValue().split("\n", -1)) {
                sb.append(key);
                sb.append(": ");
                sb.append(ln);
                sb.append('\n');
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    public String[] keys() {
        Set<String> ks = fields.keySet();
        return ks.toArray(new String[ks.size()]);
    }

    public String get(String key) {
        return fields.get(key);
    }

    public void put(String key, String value) {
        checkKey(key);
        fields.put(key, value);
    }

    public ServerEventImpl update(String... params) {
        int len = params.length;
        if (len % 2 != 0)
            throw new IllegalArgumentException("Bad argument count");
        for (int i = 0; i < len; i += 2)
            put(params[i], params[i + 1]);
        return this;
    }

    public String remove(String key) {
        return fields.remove(key);
    }

    public Map<String, String> collection() {
        return fields;
    }

    public String keepalive() {
        return ":\n\n";
    }

    public static void checkKey(String k) {
        if (k == null || k.matches("(?s).*(\\s|:)"))
            throw new IllegalArgumentException("Bad SSE key");
    }

}
