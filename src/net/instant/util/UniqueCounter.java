package net.instant.util;

public class UniqueCounter {

    private long lastTime;
    private int sequence;

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

    public String getString() {
        long v = get();
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

}
