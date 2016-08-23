package net.instant.proto;

import net.instant.api.MessageContents;
import net.instant.util.Util;
import org.json.JSONObject;

public class Message implements MessageContents {

    private String id;
    private long timestamp;
    private String type;
    private String from;
    private String to;
    private Object seq;
    private Object data;

    public Message(String type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }
    public Message() {
        this(null);
    }

    public JSONObject makeObject() {
        JSONObject ret = new JSONObject();
        if (id   != null) ret.put("id",   id  );
        if (type != null) ret.put("type", type);
        if (from != null) ret.put("from", from);
        if (to   != null) ret.put("to",   to  );
        if (seq  != null) ret.put("seq",  seq );
        if (data != null) ret.put("data", data);
        ret.put("timestamp", timestamp);
        return ret;
    }
    public String makeString() {
        return makeObject().toString();
    }

    public String id() {
        return id;
    }
    public Message id(String id) {
        this.id = id;
        return this;
    }
    public String getID() {
        return id;
    }
    public void setID(String id) {
        this.id = id;
    }

    public long timestamp() {
        return timestamp;
    }
    public Message timestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String type() {
        return type;
    }
    public Message type(String type) {
        this.type = type;
        return this;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

    public String from() {
        return from;
    }
    public Message from(String from) {
        this.from = from;
        return this;
    }
    public String getFrom() {
        return from;
    }
    public void setFrom(String from) {
        this.from = from;
    }

    public String to() {
        return to;
    }
    public Message to(String to) {
        this.to = to;
        return this;
    }
    public String getTo() {
        return to;
    }
    public void setTo(String to) {
        this.to = to;
    }

    public Object seq() {
        return seq;
    }
    public Message seq(Object seq) {
        this.seq = seq;
        return this;
    }
    public Object getSequence() {
        return seq;
    }
    public void setSequence(Object seq) {
        this.seq = seq;
    }

    public Object data() {
        return data;
    }
    public Message data(Object data) {
        this.data = data;
        return this;
    }
    public Object getData() {
        return data;
    }
    public void setData(Object data) {
        this.data = data;
    }

    public Message makeData(Object... data) {
        return data(Util.createJSONObject(data));
    }
    public Message mergeData(Object... data) {
        JSONObject add = Util.createJSONObject(data);
        if (this.data instanceof JSONObject) {
            Util.mergeJSONObjects((JSONObject) this.data, add);
        } else {
            this.data = add;
        }
        return this;
    }

}
