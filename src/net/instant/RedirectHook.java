package net.instant;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;

public class RedirectHook extends HookAdapter {

    public static final Pattern GROUPING_RE =
        Pattern.compile("\\\\([0-9]+|[^0-9])");

    private final Pattern pattern;
    private final String replacement;

    public RedirectHook(Pattern pattern, String replacement) {
        this.pattern = pattern;
        this.replacement = replacement;
    }
    public RedirectHook(String pattern, String replacement) {
        this(Pattern.compile(pattern), replacement);
    }

    public boolean allowUnassigned() {
        return false;
    }

    public void postProcessRequest(InstantWebSocketServer parent,
                                   InformationCollector.Datum info,
                                   ClientHandshake request,
                                   ServerHandshakeBuilder response,
                                   Handshakedata eff_resp) {
        if (! (parent.getEffectiveDraft(info) instanceof Draft_Raw)) return;
        if (! "GET".equals(info.getMethod())) return;
        Matcher m = pattern.matcher(request.getResourceDescriptor());
        if (m.matches()) {
            info.setResponseInfo(response, (short) 301,
                                 "Moved Permanently", -1);
            response.put("Location", expand(m, replacement));
            parent.assign(info, this);
        }
    }

    public void onOpen(InformationCollector.Datum info,
                       WebSocket conn, ClientHandshake handshake) {
        conn.close();
    }

    public static String expand(Matcher m, String repl) {
        Matcher rm = GROUPING_RE.matcher(repl);
        StringBuffer sb = new StringBuffer();
        while (rm.find()) {
            String g = rm.group(1);
            if (g.equals("\\")) {
                sb.append("\\");
            } else if (g.matches("[0-9]+")) {
                rm.appendReplacement(sb, m.group(Integer.parseInt(g)));
            } else {
                throw new RuntimeException("Invalid replacement!");
            }
        }
        rm.appendTail(sb);
        return sb.toString();
    }

}
