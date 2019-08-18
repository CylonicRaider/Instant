package net.instant.hooks;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.instant.Main;
import net.instant.api.API1;
import net.instant.api.ClientConnection;
import net.instant.api.Cookie;
import net.instant.api.Message;
import net.instant.api.MessageContents;
import net.instant.api.MessageHook;
import net.instant.api.PresenceChange;
import net.instant.api.RequestData;
import net.instant.api.ResponseBuilder;
import net.instant.api.Room;
import net.instant.proto.MessageDistributor;
import net.instant.proto.ProtocolError;
import net.instant.proto.RoomDistributor;
import net.instant.util.Formats;
import net.instant.util.UniqueCounter;
import net.instant.util.Util;
import org.json.JSONException;
import org.json.JSONObject;

public class APIWebSocketHook extends WebSocketHook {

    private static final Logger LOGGER = Logger.getLogger("APIWSHook");

    private static final String K_INSECURE = "instant.cookies.insecure";

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
            this.message = new MessageContents((present) ? "joined" : "left");
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
            this.data = new MessageContents(parsedData);
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

        public void sendResponse(MessageContents resp) {
            resp.setSequence(data.getSequence());
            source.getConnection().send(resp.toString());
        }

        public MessageContents makeMessage(String type) {
            return new MessageContents(type);
        }

    }

    public static final String COOKIE_NAME = "uid";

    private final List<MessageHook> hooks;
    private final List<MessageHook> internalHooks;
    private final boolean insecureCookies;
    private API1 api;
    private MessageDistributor distr;

    public APIWebSocketHook(API1 apiImpl, MessageDistributor distributor) {
        hooks = new ArrayList<MessageHook>();
        internalHooks = new ArrayList<MessageHook>();
        insecureCookies = Util.isTrue(apiImpl.getConfiguration(K_INSECURE));
        api = apiImpl;
        distr = distributor;
    }
    public APIWebSocketHook(API1 api) {
        this(api, null);
    }

    public API1 getAPI() {
        return api;
    }
    public void setAPI(API1 a) {
        api = a;
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
        req.getPrivateData().put("room", tag);
        Cookie cookie = req.getCookie(COOKIE_NAME);
        JSONObject data;
        if (cookie == null || cookie.getData() == null) {
            data = new JSONObject();
        } else {
            data = cookie.getData();
        }
        long id = UniqueCounter.INSTANCE.get();
        UUID uuid;
        try {
            uuid = UUID.fromString(data.getString("uuid"));
        } catch (Exception exc) {
            uuid = UniqueCounter.INSTANCE.getUUID(id);
        }
        req.getExtraData().put("uuid", uuid);
        req.getExtraData().put("id", UniqueCounter.INSTANCE.getString(id));
        data.put("uuid", uuid.toString());
        cookie = resp.makeCookie(COOKIE_NAME, data);
        cookie.updateAttributes("Path", "/", "HttpOnly", null,
            "Expires", Formats.formatHttpTime(Util.calendarIn(Calendar.YEAR,
                                                              2)));
        if (! insecureCookies) cookie.put("Secure", null);
        resp.addCookie(cookie);
        return true;
    }

    public void onOpen(ClientConnection conn) {
        String id = (String) conn.getExtraData().get("id");
        UUID uuid = (UUID) conn.getExtraData().get("uuid");
        String roomName = (String) conn.getPrivateData().get("room");
        if (roomName.equals("")) roomName = null;
        RoomDistributor room = distr.getRoom(roomName);
        MessageContents identity = new MessageContents("identity").withData(
            "id", id, "uuid", uuid,
            "version", Main.VERSION, "revision", Main.FINE_VERSION,
            "era", api.getCounter().getEra());
        PresenceChange event = new PresenceChangeImpl(true, conn, room);
        event.getMessage().updateData("id", id, "uuid", uuid);
        for (MessageHook h : getAllHooks())
            h.onConnect(event, identity);
        room.sendUnicast(conn, identity);
        distr.add(conn, room);
        if (roomName != null)
            room.sendBroadcast(event.getMessage());
    }

    public void onInput(ClientConnection conn, ByteBuffer data) {
        distr.getRoom((String) null).sendUnicast(conn,
            ProtocolError.NOT_TEXT.makeMessage());
    }

    public void onInput(ClientConnection conn, String data) {
        RoomDistributor room = distr.getRoom(conn);
        if (room == null)
            LOGGER.warning("Connection " + conn + " has a null room?!");
        Message event;
        try {
            event = new MessageImpl(data, conn, room);
        } catch (JSONException exc) {
            room.sendUnicast(conn, ProtocolError.INVALID_JSON.makeMessage());
            return;
        }
        if (event.getData().getType() == null) {
            event.sendResponse(ProtocolError.INVALID_TYPE.makeMessage());
            return;
        }
        for (MessageHook h : getAllHooks()) {
            if (h.onMessage(event)) return;
        }
        event.sendResponse(ProtocolError.INVALID_TYPE.makeMessage(
            "type", event.getData().getType()));
    }

    public void onClose(ClientConnection conn, boolean normal) {
        RoomDistributor room = distr.remove(conn);
        PresenceChange event = new PresenceChangeImpl(false, conn, room);
        event.getMessage().updateData("id", conn.getExtraData().get("id"));
        for (MessageHook h : getAllHooks())
            h.onDisconnect(event);
        if (room == null) {
            LOGGER.warning("Closing connection " + conn + " with a null " +
                "room?!");
        } else if (room.getName() != null) {
            room.sendBroadcast(event.getMessage());
        }
    }

    public void onError(ClientConnection conn, Exception exc) {
        /* NOP */
    }

}
