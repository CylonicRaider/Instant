package net.instant.hooks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.instant.info.Datum;
import net.instant.info.InformationCollector;
import net.instant.info.RequestInfo;
import net.instant.ws.Draft_Raw;
import net.instant.ws.InstantWebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;

public class RedirectHook extends HookAdapter {

    public static final Pattern GROUPING_RE =
        Pattern.compile("\\\\([0-9]+|\\{[0-9]+\\}|[^0-9{])");

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
                                   RequestInfo info,
                                   Handshakedata eff_resp) {
        if (! (parent.getEffectiveDraft(info) instanceof Draft_Raw)) return;
        if (! "GET".equals(info.getBase().getMethod())) return;
        Matcher m = pattern.matcher(info.getBase().getURL());
        if (m.matches()) {
            info.respond(301, "Moved Permanently", -1);
            info.putHeader("Location", expand(m, replacement));
            parent.assign(info, this);
        }
    }

    public void onOpen(RequestInfo info, ClientHandshake handshake) {
        info.getConnection().close();
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
            } else if (g.matches("\\{[0-9]+\\}")) {
                rm.appendReplacement(sb, m.group(Integer.parseInt(
                    g.substring(1, g.length() - 1))));
            } else {
                throw new RuntimeException("Invalid replacement!");
            }
        }
        rm.appendTail(sb);
        return sb.toString();
    }

}
