package net.instant.proto;

import net.instant.info.RequestInfo;
import org.json.JSONObject;
import org.json.JSONException;

public class MessageInfo implements net.instant.api.Message {

    private final String rawData;
    private final JSONObject parsedData;
    private final Message data;
    private final RequestInfo source;
    private final RoomDistributor room;

    public MessageInfo(String rawData, RequestInfo source,
                       RoomDistributor room) throws JSONException {
        this.rawData = rawData;
        this.parsedData = new JSONObject(rawData);
        this.data = new Message(this.parsedData);
        this.source = source;
        this.room = room;
    }

    public String getRawData() {
        return rawData;
    }
    public JSONObject getParsedData() {
        return parsedData;
    }
    public Message getData() {
        return data;
    }

    public RequestInfo getSource() {
        return source;
    }
    public RoomDistributor getRoom() {
        return room;
    }

    public Message makeReply(String type) {
        return new Message(type).seq(data.seq());
    }

}
