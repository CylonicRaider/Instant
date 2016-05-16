package net.instant.proto;

import net.instant.util.Util;
import org.json.JSONObject;

public class Message {

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

    public long timestamp() {
        return timestamp;
    }
    public Message timestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public String type() {
        return type;
    }
    public Message type(String type) {
        this.type = type;
        return this;
    }

    public String from() {
        return from;
    }
    public Message from(String from) {
        this.from = from;
        return this;
    }

    public String to() {
        return to;
    }
    public Message to(String to) {
        this.to = to;
        return this;
    }

    public Object seq() {
        return seq;
    }
    public Message seq(Object seq) {
        this.seq = seq;
        return this;
    }

    public Object data() {
        return data;
    }
    public Message data(Object data) {
        this.data = data;
        return this;
    }

    public Message makeData(Object... data) {
        return data(Util.createJSONObject(data));
    }

}
