package net.instant.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.instant.InformationCollector;
import net.instant.InstantWebSocketServer;
import net.instant.Main;
import net.instant.proto.Message;
import net.instant.proto.MessageDistributor;
import net.instant.proto.ProtocolError;
import net.instant.util.Util;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.json.JSONObject;
import org.json.JSONException;

public class RoomWebSocketHook extends WebSocketHook {

    public static final String ROOM_PREF = "/room/";
    public static final String ROOM_POSTF = "/ws";

    private MessageDistributor distr;

    public RoomWebSocketHook(MessageDistributor d) {
        distr = d;
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

    public void onOpen(InformationCollector.Datum info,
                       WebSocket conn, ClientHandshake handshake) {
        String url = handshake.getResourceDescriptor();
        if (! url.substring(0, ROOM_PREF.length()).equals(ROOM_PREF))
            return;
        int cutoff = url.length() - ROOM_POSTF.length();
        if (! url.substring(cutoff, url.length()).equals(ROOM_POSTF))
            return;
        String name = url.substring(ROOM_PREF.length(), cutoff);
        String id = distr.makeID();
        info.getExtra().put("id", id);
        conn.send(new Message("identity").makeData("id", id,
            "version", Main.VERSION,
            "revision", Main.FINE_VERSION).makeString());
        distr.add(name, conn, id);
        distr.get(conn).broadcast(prepare("joined").makeData("id", id));
    }

    public void onMessage(WebSocket conn, String message) {
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
        String from = distr.connectionID(conn);
        if ("ping".equals(type)) {
            conn.send(new Message("pong").seq(seq).data(d).makeString());
        } else if ("unicast".equals(type)) {
            /* Flow analysis should show that this will never be
             * uninitialized, but if statements are exempt from
             * it... :( */
            String to = null;
            WebSocket target;
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
            target.send(msg.makeString());
        } else if ("broadcast".equals(type)) {
            Message msg = prepare("broadcast");
            msg.from(from).data(d);
            conn.send(new Message("reply").seq(seq).makeData("id",
                msg.id(), "type", "broadcast").makeString());
            distr.get(conn).broadcast(msg);
        } else {
            sendError(conn, ProtocolError.INVALID_TYPE);
        }
    }

    public void onClose(WebSocket conn, int code, String reason,
                        boolean remote) {
        MessageDistributor.RoomDistributor room = distr.get(conn);
        String id = distr.connectionID(conn);
        distr.remove(conn);
        room.broadcast(prepare("left").makeData("id", id));
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
