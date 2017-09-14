package net.instant.proto;

import net.instant.api.MessageContents;
import net.instant.util.Util;
import org.json.JSONException;
import org.json.JSONObject;

public class MessageData implements MessageContents {

    private String id;
    private Object seq;
    private String type;
    private String from;
    private String to;
    private Object data;
    private long timestamp;

    public MessageData(JSONObject source) throws JSONException {
        id = source.optString("id");
        seq = source.opt("seq");
        type = source.getString("type");
        from = source.optString("from");
        to = source.optString("to");
        data = source.opt("data");
        Long ts = source.optLong("timestamp");
        if (ts == null) ts = System.currentTimeMillis();
        timestamp = ts;
    }
    public MessageData(MessageContents source) {
        id = source.getID();
        seq = source.getSequence();
        type = source.getType();
        from = source.getFrom();
        to = source.getTo();
        data = source.getData();
        timestamp = source.getTimestamp();
    }
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
    public MessageData id(String id) {
        this.id = id;
        return this;
    }

    public Object getSequence() {
        return seq;
    }
    public void setSequence(Object seq) {
        this.seq = seq;
    }
    public MessageData sequence(Object seq) {
        this.seq = seq;
        return this;
    }

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public MessageData type(String type) {
        this.type = type;
        return this;
    }

    public String getFrom() {
        return from;
    }
    public void setFrom(String from) {
        this.from = from;
    }
    public MessageData from(String from) {
        this.from = from;
        return this;
    }

    public String getTo() {
        return to;
    }
    public void setTo(String to) {
        this.to = to;
    }
    public MessageData to(String to) {
        this.to = to;
        return this;
    }

    public Object getData() {
        return data;
    }
    public void setData(Object data) {
        this.data = data;
    }
    public MessageData data(Object data) {
        this.data = data;
        return this;
    }

    public void updateData(Object... params) {
        if (data instanceof JSONObject) {
            Util.mergeJSONObjects((JSONObject) data,
                Util.createJSONObject(params));
        } else {
            data = Util.createJSONObject(params);
        }
    }
    public MessageData withData(Object... params) {
        updateData(params);
        return this;
    }

    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long ts) {
        this.timestamp = ts;
    }
    public MessageData timestamp(long ts) {
        this.timestamp = ts;
        return this;
    }

}
