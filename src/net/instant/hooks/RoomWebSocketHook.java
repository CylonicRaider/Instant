package net.instant.hooks;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.instant.Main;
import net.instant.api.Cookie;
import net.instant.api.RequestResponseData;
import net.instant.api.Room;
import net.instant.info.Datum;
import net.instant.info.InformationCollector;
import net.instant.info.RequestInfo;
import net.instant.proto.Message;
import net.instant.proto.MessageDistributor;
import net.instant.proto.MessageInfo;
import net.instant.proto.PresenceChangeInfo;
import net.instant.proto.ProtocolError;
import net.instant.proto.RoomDistributor;
import net.instant.util.Formats;
import net.instant.util.UniqueCounter;
import net.instant.util.Util;
import net.instant.ws.InstantWebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.json.JSONException;
import org.json.JSONObject;

public class RoomWebSocketHook extends WebSocketHook {

    private static final Logger LOGGER = Logger.getLogger("RoomWSHook");

    public interface Hook {

        void processJoin(PresenceChangeInfo info, Message msg);

        boolean processMessage(MessageInfo msg);

        void processLeave(PresenceChangeInfo info);

    }

    public static final String ROOM_PREF = "/room/";
    public static final String ROOM_POSTF = "/ws";
    public static final String COOKIE_NAME = "uid";

    private final Map<RequestInfo, InstantWebSocketServer> parents;
    private MessageDistributor distr;
    private Hook hook;

    public RoomWebSocketHook(MessageDistributor distr) {
        this.parents = Collections.synchronizedMap(
            new HashMap<RequestInfo, InstantWebSocketServer>());
        this.distr = distr;
        whitelist(ROOM_PREF + Main.ROOM_RE + ROOM_POSTF);
    }
    public RoomWebSocketHook() {
        this(null);
    }

    public MessageDistributor getDistributor() {
        return distr;
    }
    public void setDistributor(MessageDistributor md) {
        distr = md;
    }

    public Hook getHook() {
        return hook;
    }
    public void setHook(Hook h) {
        hook = h;
    }

    protected void postProcessRequestInner(InstantWebSocketServer parent,
                                           RequestInfo info,
                                           Handshakedata eff_resp) {
        List<Cookie> l = info.getCookies();
        Cookie cookie = null;
        for (Cookie c : l) {
            if (c.getName().equals(COOKIE_NAME)) {
                cookie = c;
                break;
            }
        }
        JSONObject data;
        if (cookie == null || cookie.getData() == null) {
            cookie = info.makeCookie(COOKIE_NAME, "");
            data = new JSONObject();
        } else {
            data = cookie.getData();
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(data.getString("uuid"));
        } catch (Exception exc) {
            uuid = MessageDistributor.makeUUID();
        }
        info.getExtraData().put("uuid", uuid);
        data.put("uuid", uuid.toString());
        cookie.setData(data);
        cookie.put("Path", "/");
        cookie.put("HttpOnly", null);
        Calendar expiry = Calendar.getInstance();
        expiry.add(Calendar.YEAR, 2);
        cookie.put("Expires", Formats.formatHttpTime(expiry));
        if (! InstantWebSocketServer.INSECURE_COOKIES)
            cookie.put("Secure", null);
        info.putCookie(cookie);
        parents.put(info, parent);
    }

    public void onOpen(RequestInfo info, ClientHandshake handshake) {
        WebSocket conn = info.getConnection();
        String url = handshake.getResourceDescriptor();
        if (! url.substring(0, ROOM_PREF.length()).equals(ROOM_PREF))
            return;
        int cutoff = url.length() - ROOM_POSTF.length();
        if (! url.substring(cutoff, url.length()).equals(ROOM_POSTF))
            return;
        String name = url.substring(ROOM_PREF.length(), cutoff);
        String id = MessageDistributor.makeID();
        UUID uuid = (UUID) info.getExtraData().get("uuid");
        info.getExtraData().put("id", id);
        Message identity = new Message("identity").makeData("id", id,
            "uuid", uuid, "version", Main.VERSION,
            "revision", Main.FINE_VERSION);
        Message joined = prepare("joined").makeData("id", id, "uuid", uuid);
        if (hook != null)
            hook.processJoin(new PresenceChangeInfo(true, info,
                distr.get(name), joined), identity);
        conn.send(identity.makeString());
        distr.add(name, info, id);
        distr.get(info).broadcast(joined);
    }

    public void onMessage(RequestInfo info, String message) {
        WebSocket conn = info.getConnection();
        MessageInfo msgInfo;
        try {
            msgInfo = new MessageInfo(message, info, distr.get(info));
        } catch (JSONException e) {
            sendError(conn, ProtocolError.INVALID_JSON);
            return;
        }
        if (hook != null && hook.processMessage(msgInfo)) return;
        JSONObject data = msgInfo.getParsedData();
        String type = msgInfo.getData().getType();
        if (type == null || type.isEmpty()) {
            sendError(conn, ProtocolError.INVALID_TYPE);
            return;
        }
        Object seq = data.opt("seq");
        Object d = data.opt("data");
        String from = distr.connectionID(info);
        if ("ping".equals(type)) {
            conn.send(new Message("pong").seq(seq).makeString());
            InstantWebSocketServer srv = parents.get(info);
            if (d instanceof JSONObject) {
                Long next = ((JSONObject) d).optLong("next");
                srv.setDeadline(info, next);
            } else {
                srv.setDeadline(info, null);
            }
        } else if ("unicast".equals(type)) {
            String to = null;
            RequestResponseData target = null;
            try {
                to = data.getString("to");
                target = distr.connection(to);
            } catch (JSONException e) {
                /* Handled below */
            }
            if (target == null) {
                sendMessage(conn, ProtocolError.NO_PARTICIPANT.makeMessage(
                    Util.createJSONObject("id", to)).seq(seq));
                return;
            }
            Message msg = prepare("unicast");
            msg.from(from).to(to).data(d);
            conn.send(new Message("reply").seq(seq).makeData("id",
                msg.id(), "type", "unicast").makeString());
            target.getConnection().send(msg.makeString());
        } else if ("broadcast".equals(type)) {
            Message msg = prepare("broadcast");
            msg.from(from).data(d);
            conn.send(new Message("reply").seq(seq).makeData("id",
                msg.id(), "type", "broadcast").makeString());
            distr.get(info).broadcast(msg);
        } else if ("who".equals(type)) {
            Message msg = prepare("who").seq(seq);
            JSONObject rdata = new JSONObject();
            Set<RequestResponseData> clients;
            synchronized (distr) {
                clients = distr.get(info).getClients();
                for (RequestResponseData client : clients) {
                    rdata.put(distr.connectionID(client),
                        Util.createJSONObject("uuid",
                            (UUID) client.getExtraData().get("uuid")));
                }
            }
            conn.send(msg.data(rdata).makeString());
        } else {
            sendMessage(conn,
                        ProtocolError.INVALID_TYPE.makeMessage().seq(seq));
        }
    }

    public void onClose(RequestInfo info, int code, String reason,
                        boolean remote) {
        parents.remove(info);
        Room room = distr.getRoom(info);
        String id = distr.connectionID(info);
        Message left = prepare("left").makeData("id", id);
        RoomDistributor rd = distr.get(info);
        distr.remove(info);
        if (hook != null)
            hook.processLeave(new PresenceChangeInfo(false, info,
                                                     rd, left));
        if (room == null) {
            LOGGER.warning("Closing without a room (" +
                info.getConnection() + ")");
        } else {
            room.sendBroadcast(left);
        }
    }

    public Message prepare(String type) {
        return new Message(type).id(MessageDistributor.makeID());
    }

    public static void sendError(WebSocket conn, ProtocolError err,
                                 Object detail) {
        sendMessage(conn, err.makeMessage(detail));
    }
    public static void sendError(WebSocket conn, ProtocolError err) {
        sendMessage(conn, err.makeMessage());
    }

    public static void sendMessage(WebSocket conn, Message msg) {
        conn.send(msg.makeString());
    }

}
