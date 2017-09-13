package net.instant.proto;

import net.instant.api.MessageContents;
import net.instant.util.Util;
import org.json.JSONObject;

public class MessageData implements MessageContents {

    private String id;
    private Object seq;
    private String type;
    private String from;
    private String to;
    private Object data;
    private long timestamp;

    public MessageData(String id, String type) {
        this.id = id;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }
    public MessageData(String type) {
        this(null, type);
    }
    public MessageData() {
        this(null, null);
    }

    public String toString() {
        return Util.createJSONObject("id", id, "type", type, "seq", seq,
            "from", from, "to", to, "data", data, "timestamp", timestamp
        ).toString();
    }

    public String getID() {
        return id;
    }
    public void setID(String id) {
        this.id = id;
    }

    public Object getSequence() {
        return seq;
    }
    public void setSequence(Object seq) {
        this.seq = seq;
    }

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

    public String getFrom() {
        return from;
    }
    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }
    public void setTo(String to) {
        this.to = to;
    }

    public Object getData() {
        return data;
    }
    public void setData(Object data) {
        this.data = data;
    }

    public void updateData(Object... params) {
        if (data instanceof JSONObject) {
            Util.mergeJSONObjects((JSONObject) data,
                Util.createJSONObject(params));
        } else {
            data = Util.createJSONObject(params);
        }
    }

    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long ts) {
        this.timestamp = ts;
    }

}
