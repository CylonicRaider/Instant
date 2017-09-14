package net.instant.proto;

import java.util.UUID;
import net.instant.api.ClientConnection;
import net.instant.api.Message;
import net.instant.api.MessageContents;
import net.instant.api.MessageHook;
import net.instant.api.PresenceChange;
import net.instant.api.Room;
import net.instant.util.UniqueCounter;
import net.instant.util.Util;
import org.json.JSONObject;

public class APIHook implements MessageHook {

    public void onConnect(PresenceChange change, MessageContents greeting) {
        /* NOP */
    }

    public boolean onMessage(Message message) {
        switch (message.getData().getType()) {
            case "ping":
                return handlePing(message);
            case "unicast":
                return handleUnicast(message);
            case "broadcast":
                return handleBroadcast(message);
            case "who":
                return handleWho(message);
            default:
                return false;
        }
    }

    public void onDisconnect(PresenceChange change) {
        /* NOP */
    }

    protected boolean handlePing(Message msg) {
        msg.sendResponse(new MessageData("pong"));
        Object data = msg.getData().getData();
        if (data instanceof JSONObject) {
            Long next = ((JSONObject) data).optLong("next");
            msg.getSource().setDeadline(next);
        } else {
            msg.getSource().setDeadline(null);
        }
        return true;
    }

    protected boolean handleUnicast(Message msg) {
        MessageContents cnt = msg.getData();
        ClientConnection recipient = msg.getRoom().getGroup().getClient(
            cnt.getTo());
        if (recipient == null) {
            msg.sendResponse(ProtocolError.NO_PARTICIPANT.makeMessage(
                "id", cnt.getTo()));
            return true;
        }
        String id = UniqueCounter.INSTANCE.getString();
        msg.sendResponse(new MessageData("response").withData("id", id,
            "type", "unicast"));
        msg.getRoom().sendUnicast(recipient, new MessageData("unicast")
            .id(id).from((String) msg.getSource().getExtraData().get("id"))
            .to(cnt.getTo()).data(cnt.getData()));
        return true;
    }

    protected boolean handleBroadcast(Message msg) {
        if (msg.getRoom().getName() == null) return false;
        String id = UniqueCounter.INSTANCE.getString();
        msg.sendResponse(new MessageData("response").withData("id", id,
            "type", "broadcast"));
        msg.getRoom().sendBroadcast(new MessageData("broadcast").id(id)
            .from((String) msg.getSource().getExtraData().get("id"))
            .data(msg.getData().getData()));
        return true;
    }

    protected boolean handleWho(Message msg) {
        JSONObject rdata = new JSONObject();
        Room room = msg.getRoom();
        synchronized (room) {
            for (ClientConnection conn : room.getClients()) {
                String id = (String) conn.getExtraData().get("id");
                UUID uuid = (UUID) conn.getExtraData().get("uuid");
                rdata.put(id, Util.createJSONObject("uuid", uuid));
            }
        }
        msg.sendResponse(new MessageData("who").data(rdata));
        return true;
    }

}
