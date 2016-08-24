package net.instant.info;

import java.util.IdentityHashMap;
import java.util.Map;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;

public class InformationCollector {

    public interface Hook {

        void postProcessRequest(ClientHandshake request,
                                ServerHandshakeBuilder response,
                                Handshakedata eff_resp);

    }

    private Map<Handshakedata, Datum> mapping =
        new IdentityHashMap<Handshakedata, Datum>();
    private Hook hook;

    public InformationCollector(Hook hook) {
        super();
        this.hook = hook;
    }
    public InformationCollector() {
        this(null);
    }

    public Datum get(Handshakedata data) {
        Datum entry = mapping.get(data);
        if (entry == null) {
            entry = new Datum();
            mapping.put(data, entry);
        }
        return entry;
    }
    public Datum pop(Handshakedata data) {
        return mapping.remove(data);
    }

    public Hook getHook() {
        return this.hook;
    }
    public void setHook(Hook hook) {
        this.hook = hook;
    }

    public void addWebSocket(ClientHandshake request, WebSocket sock,
                             Draft draft) {
        get(request).setWebSocket(sock);
        get(request).setDraft(draft);
        get(request).setSourceAddress(sock.getRemoteSocketAddress());
    }

    public void addRequestLine(Handshakedata data, String line) {
        get(data).setRequestLine(line);
    }

    public void postProcess(ClientHandshake request,
                            ServerHandshakeBuilder response,
                            Handshakedata eff_resp) {
        get(request).setReferer(getValue(request, "Referer"));
        get(request).setUserAgent(getValue(request, "User-Agent"));
        String fwd = getValue(request, "X-Forwarded-For");
        if (fwd != null) fwd = fwd.replace(" ", "");
        get(request).getExtra().put("real-ip", fwd);
        if (hook != null) hook.postProcessRequest(request, response, eff_resp);
    }

    public void reset() {
        mapping.clear();
    }

    private String getValue(ClientHandshake request, String k) {
        String v = request.getFieldValue(k);
        return (v == null || v.isEmpty()) ? null : v;
    }

}
