package net.instant.hooks;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import net.instant.InstantWebSocketServer;
import net.instant.Main;
import net.instant.api.RequestResponseData;
import net.instant.api.Room;
import net.instant.info.Datum;
import net.instant.info.InformationCollector;
import net.instant.info.RequestInfo;
import net.instant.proto.Message;
import net.instant.proto.MessageDistributor;
import net.instant.proto.ProtocolError;
import net.instant.util.UniqueCounter;
import net.instant.util.Util;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.json.JSONException;
import org.json.JSONObject;

public class RoomWebSocketHook extends WebSocketHook {

    public static final String ROOM_PREF = "/room/";
    public static final String ROOM_POSTF = "/ws";
    public static final String COOKIE_NAME = "uid";

    private MessageDistributor distr;

    public RoomWebSocketHook() {
        whitelist(ROOM_PREF + Main.ROOM_RE + ROOM_POSTF);
    }

    public MessageDistributor getDistributor() {
        return distr;
    }
    public void setDistributor(MessageDistributor md) {
        distr = md;
    }

    protected void postProcessRequestInner(InstantWebSocketServer parent,
                                           Datum info,
                                           ClientHandshake request,
                                           ServerHandshakeBuilder response,
                                           Handshakedata eff_resp) {
        CookieHandler handler = parent.getCookieHandler();
        if (handler == null) return;
        List<CookieHandler.Cookie> l = handler.get(request);
        CookieHandler.Cookie cookie = null;
        for (CookieHandler.Cookie c : l) {
            if (c.getName().equals(COOKIE_NAME)) {
                cookie = c;
                break;
            }
        }
        JSONObject data;
        if (cookie == null || cookie.getData() == null) {
            cookie = handler.make(COOKIE_NAME, "");
            data = new JSONObject();
        } else {
            data = cookie.getData();
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(data.getString("uuid"));
        } catch (Exception exc) {
            uuid = distr.makeUUID();
        }
        info.getExtra().put("uuid", uuid);
        data.put("uuid", uuid.toString());
        cookie.setData(data);
        cookie.put("Path", "/");
        cookie.put("HttpOnly", null);
        Calendar expiry = Calendar.getInstance();
        expiry.add(Calendar.YEAR, 2);
        cookie.put("Expires", Util.formatHttpTime(expiry));
        if (! InstantWebSocketServer.INSECURE_COOKIES)
            cookie.put("Secure", null);
        handler.set(response, cookie);
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
        String id = distr.makeID();
        UUID uuid = (UUID) info.getExtraData().get("uuid");
        info.getExtraData().put("id", id);
        conn.send(new Message("identity").makeData("id", id, "uuid", uuid,
            "version", Main.VERSION,
            "revision", Main.FINE_VERSION).makeString());
        distr.add(name, info, id);
        distr.get(info).broadcast(prepare("joined").makeData("id", id,
            "uuid", uuid));
    }

    public void onMessage(RequestInfo info, String message) {
        WebSocket conn = info.getConnection();
        JSONObject data;
        try {
            data = new JSONObject(message);
        } catch (JSONException e) {
            sendError(conn, ProtocolError.INVALID_JSON);
            return;
        }
        String type;
        try {
            type = data.getString("type");
        } catch (JSONException e) {
            sendError(conn, ProtocolError.INVALID_TYPE);
            return;
        }
        Object seq = data.opt("seq");
        Object d = data.opt("data");
        String from = distr.connectionID(info);
        if ("ping".equals(type)) {
            conn.send(new Message("pong").seq(seq).data(d).makeString());
        } else if ("unicast".equals(type)) {
            /* Flow analysis should show that this will never be
             * uninitialized, but if statements are exempt from
             * it... :( */
            String to = null;
            RequestResponseData target;
            try {
                to = data.getString("to");
                target = distr.connection(to);
            } catch (JSONException e) {
                target = null;
            }
            if (target == null) {
                sendMessage(conn,
                    initError(ProtocolError.NO_SUCH_PARTICIPANT,
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
        } else {
            sendError(conn, ProtocolError.INVALID_TYPE);
        }
    }

    public void onClose(RequestInfo info, int code, String reason,
                        boolean remote) {
        Room room = distr.getRoom(info);
        String id = distr.connectionID(info);
        distr.remove(info);
        room.sendBroadcast(prepare("left").makeData("id", id));
    }

    public Message prepare(String type) {
        return new Message(type).id(distr.makeID());
    }

    public static void sendError(WebSocket conn, ProtocolError err) {
        sendMessage(conn, initError(err));
    }
    public static void sendError(WebSocket conn, int code, String message) {
        sendMessage(conn, initError(code, message));
    }

    public static Message initError(int code, String message,
                                    Object detail) {
        return new Message("error").makeData("code", code,
            "message", message, "detail", detail);
    }
    public static Message initError(int code, String message) {
        return new Message("error").makeData("code", code,
            "message", message);
    }
    public static Message initError(ProtocolError err, Object detail) {
        return initError(err.getCode(), err.getMessage(), detail);
    }
    public static Message initError(ProtocolError err) {
        return initError(err.getCode(), err.getMessage());
    }

    public static void sendMessage(WebSocket conn, Message msg) {
        conn.send(msg.makeString());
    }

}
