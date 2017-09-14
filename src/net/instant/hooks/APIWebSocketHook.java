package net.instant.hooks;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.instant.Main;
import net.instant.api.ClientConnection;
import net.instant.api.Cookie;
import net.instant.api.Message;
import net.instant.api.MessageContents;
import net.instant.api.MessageHook;
import net.instant.api.PresenceChange;
import net.instant.api.RequestData;
import net.instant.api.ResponseBuilder;
import net.instant.api.Room;
import net.instant.proto.MessageData;
import net.instant.proto.MessageDistributor;
import net.instant.proto.ProtocolError;
import net.instant.proto.RoomDistributor;
import net.instant.util.Formats;
import net.instant.util.UniqueCounter;
import net.instant.util.Util;
import net.instant.ws.InstantWebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

public class APIWebSocketHook extends WebSocketHook {

    public static class PresenceChangeImpl implements PresenceChange {

        private final boolean present;
        private final ClientConnection source;
        private final Room room;
        private final MessageContents message;

        public PresenceChangeImpl(boolean present, ClientConnection source,
                                  Room room) {
            this.present = present;
            this.source = source;
            this.room = room;
            this.message = new MessageData((present) ? "joined" : "left");
        }

        public boolean isPresent() {
            return present;
        }

        public ClientConnection getSource() {
            return source;
        }

        public Room getRoom() {
            return room;
        }

        public MessageContents getMessage() {
            return message;
        }

    }

    public static class MessageImpl implements Message {

        private final String rawData;
        private final JSONObject parsedData;
        private final MessageContents data;
        private final ClientConnection source;
        private final Room room;

        public MessageImpl(String rawData, ClientConnection source,
                           Room room) throws JSONException {
            this.rawData = rawData;
            this.parsedData = new JSONObject(rawData);
            this.data = new MessageData(parsedData);
            this.source = source;
            this.room = room;
       }

        public String getRawData() {
            return rawData;
        }

        public JSONObject getParsedData() {
            return parsedData;
        }

        public MessageContents getData() {
            return data;
        }

        public ClientConnection getSource() {
            return source;
        }

        public Room getRoom() {
            return room;
        }

        public MessageContents makeResponse(String type) {
            MessageContents ret = new MessageData(type);
            ret.setSequence(data.getSequence());
            return ret;
        }

    }

    public static final String COOKIE_NAME = "uid";

    private final List<MessageHook> hooks;
    private final List<MessageHook> internalHooks;
    private final Map<RequestData, String> tags;
    private MessageDistributor distr;

    public APIWebSocketHook(MessageDistributor distributor) {
        hooks = new ArrayList<MessageHook>();
        internalHooks = new ArrayList<MessageHook>();
        tags = Collections.synchronizedMap(
            new HashMap<RequestData, String>());
        distr = distributor;
    }
    public APIWebSocketHook() {
        this(null);
    }

    public MessageDistributor getDistributor() {
        return distr;
    }
    public void setDistributor(MessageDistributor d) {
        distr = d;
    }

    public Iterable<MessageHook> getAllHooks() {
        return Util.concat(hooks, internalHooks);
    }
    public void addHook(MessageHook h) {
        hooks.add(h);
    }
    public void removeHook(MessageHook h) {
        hooks.remove(h);
    }
    public void addInternalHook(MessageHook h) {
        internalHooks.add(h);
    }
    public void removeInternalHook(MessageHook h) {
        internalHooks.remove(h);
    }

    protected boolean evaluateRequestInner(RequestData req,
            ResponseBuilder resp, String tag) {
        tags.put(req, tag);
        Cookie cookie = req.getCookie(COOKIE_NAME);
        JSONObject data;
        if (cookie == null || cookie.getData() == null) {
            data = new JSONObject();
        } else {
            data = cookie.getData();
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(data.getString("uuid"));
        } catch (Exception exc) {
            uuid = UniqueCounter.INSTANCE.getUUID();
        }
        req.getExtraData().put("uuid", uuid);
        data.put("uuid", uuid.toString());
        cookie = resp.makeCookie(COOKIE_NAME, data);
        cookie.put("Path", "/");
        cookie.put("HttpOnly", null);
        Calendar expiry = Calendar.getInstance();
        expiry.add(Calendar.YEAR, 2);
        cookie.put("Expires", Formats.formatHttpTime(expiry));
        if (! InstantWebSocketServer.INSECURE_COOKIES)
            cookie.put("Secure", null);
        resp.addCookie(cookie);
        return true;
    }

    public void onOpen(ClientConnection conn) {
        String id = UniqueCounter.INSTANCE.getString();
        conn.getExtraData().put("id", id);
        UUID uuid = (UUID) conn.getExtraData().get("uuid");
        String roomName = tags.remove(conn);
        if (roomName.equals("")) roomName = null;
        RoomDistributor room;
        room = distr.getRoom(roomName);
        MessageData identity = new MessageData("identity");
        identity.updateData("id", id, "uuid", uuid,
            "version", Main.VERSION, "revision", Main.FINE_VERSION);
        PresenceChange event = new PresenceChangeImpl(true, conn, room);
        event.getMessage().updateData("id", id, "uuid", uuid);
        for (MessageHook h : getAllHooks())
            h.onConnect(event, identity);
        conn.getConnection().send(identity.toString());
        distr.add(conn, room);
        if (roomName != null)
            room.sendBroadcast(event.getMessage());
    }

    public void onInput(ClientConnection conn, ByteBuffer data) {
        conn.getConnection().send(ProtocolError.NOT_TEXT.toString());
    }

    public void onInput(ClientConnection conn, String data) {
        RoomDistributor room = distr.getRoom(conn);
        Message event;
        try {
            event = new MessageImpl(data, conn, room);
        } catch (JSONException exc) {
            conn.getConnection().send(ProtocolError.INVALID_JSON.toString());
            return;
        }
        for (MessageHook h : getAllHooks())
            if (h.onMessage(event)) break;
    }

    public void onClose(ClientConnection conn, boolean normal) {
        RoomDistributor room = distr.remove(conn);
        PresenceChange event = new PresenceChangeImpl(false, conn, room);
        event.getMessage().updateData("id", conn.getExtraData().get("id"));
        for (MessageHook h : getAllHooks())
            h.onDisconnect(event);
        if (room.getName() != null)
            room.sendBroadcast(event.getMessage());
    }

    public void onError(ClientConnection conn, Exception exc) {
        /* NOP */
    }

}
