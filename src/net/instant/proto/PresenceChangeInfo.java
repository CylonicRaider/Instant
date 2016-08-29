package net.instant.proto;

import net.instant.api.PresenceChange;
import net.instant.info.RequestInfo;

public class PresenceChangeInfo implements PresenceChange {

    private final boolean present;
    private final RequestInfo source;
    private final RoomDistributor room;
    private final Message message;

    public PresenceChangeInfo(boolean present, RequestInfo source,
                              RoomDistributor room, Message message) {
        this.present = present;
        this.source = source;
        this.room = room;
        this.message = message;
    }

    public boolean isPresent() {
        return present;
    }

    public RequestInfo getSource() {
        return source;
    }

    public RoomDistributor getRoom() {
        return room;
    }

    public Message getMessage() {
        return message;
    }

}
