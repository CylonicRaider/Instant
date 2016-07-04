package net.instant.util;

import java.util.UUID;

public class UniqueCounter {

    /* The UNIX epoch as a UUID timestamp */
    private static final long EPOCH_UUID = 122192928000000000L;

    private long lastTime;
    private int sequence;
    private final long nodeID;

    public UniqueCounter() {
        byte[] rnd = Util.getRandomness(6);
        rnd[0] |= 1;
        nodeID = (long) rnd[0] << 40 & 0xFF0000000000L |
                 (long) rnd[1] << 32 & 0x00FF00000000L |
                 (long) rnd[2] << 24 & 0x0000FF000000L |
                 (long) rnd[3] << 16 & 0x000000FF0000L |
                 (long) rnd[4] <<  8 & 0x00000000FF00L |
                 (long) rnd[5] <<  0 & 0x0000000000FFL;
        Util.clear(rnd);
    }

    /**
     * Output format: a long, with the upper 54 bits containing a
     * millisecond-precise UNIX timestamp, and the remaining bits
     * containing a sequence number that is reset every second
     * (and not every millisecond to account for leap seconds).
     * Expected wreckage time: Around Y280K.
     */
    public synchronized long get() {
        long curTime = System.currentTimeMillis();
        sequence = (curTime != lastTime) ? 0 : (sequence + 1) & 0x3FF;
        lastTime = curTime;
        return curTime << 10 | sequence;
    }

    public String getString(long v) {
        byte[] temp = new byte[8];
        temp[0] = (byte) (v >> 56);
        temp[1] = (byte) (v >> 48);
        temp[2] = (byte) (v >> 40);
        temp[3] = (byte) (v >> 32);
        temp[4] = (byte) (v >> 24);
        temp[5] = (byte) (v >> 16);
        temp[6] = (byte) (v >>  8);
        temp[7] = (byte) (v >>  0);
        return Util.toHex(temp);
    }
    public String getString() {
        return getString(get());
    }

    public UUID getUUID(long v) {
        /* Split value into timestamp and sequence */
        long ts = v >>> 10, seq = v & 0x3FF;
        /* To get it into UUID format, we have to multiply the timestamp
         * by 10000. Now, this does in no way fit into a long -> Welcome
         * to the brave new world of 96-bit arithmetic! */
        /* Split timestamp into 32-bit low and high halves */
        long ts_hi = ts >>> 32, ts_lo = ts & 0xFFFFFFFFL;
        /* Multiply by 10000 and add Epoch offset */
        long uts_hi = ts_hi * 10000;
        long uts_lo = ts_lo * 10000 + EPOCH_UUID;
        /* Add sequence value -- stretched to span the whole range between
         * two subsequent timestamp values */
        uts_lo += seq * 10000 / 1024;
        /* Tuck carry into high two-thirds */
        uts_hi += uts_lo >>> 32;
        uts_lo &= 0xFFFFFFFFL;
        /* Assemble everything into an UUID */
        return new UUID(uts_lo << 32 | uts_hi << 16 & 0xFFFF0000L |
                        uts_hi >> 16 & 0xFFF | 0x1000,
                        uts_hi << 20 & 0x3FFF000000000000L | nodeID |
                        0x8000000000000000L);
    }
    public UUID getUUID() {
        return getUUID(get());
    }

}
