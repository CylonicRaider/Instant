package net.instant.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class RecordDigester {

    private static final String ALGORITHM = "SHA-256";

    private static final long SEED = 0x5348412d323536L; // "SHA-256"

    private static final byte TAG_FINISH = 0;
    private static final byte TAG_START  = 1;
    private static final byte TAG_NULL   = 2;
    private static final byte TAG_BYTE   = 3;
    private static final byte TAG_INT    = 4;
    private static final byte TAG_LONG   = 5;
    private static final byte TAG_BYTES  = 6;
    private static final byte TAG_STRING = 7;
    private static final byte TAG_STREAM = 8;

    private final MessageDigest digest;
    private final MessageDigest subDigest;
    private final byte[] scratch;
    private boolean initialized;

    public RecordDigester() throws NoSuchAlgorithmException {
        digest = MessageDigest.getInstance(ALGORITHM);
        subDigest = MessageDigest.getInstance(ALGORITHM);
        scratch = new byte[9];
        initialized = false;
    }

    private void addTaggedInt(byte tag, int value) {
        start();
        scratch[0] = tag;
        scratch[1] = (byte) (value >> 24);
        scratch[2] = (byte) (value >> 16);
        scratch[3] = (byte) (value >>  8);
        scratch[4] = (byte) (value      );
        digest.update(scratch, 0, 5);
    }
    private void addTaggedLong(byte tag, long value) {
        start();
        scratch[0] = tag;
        scratch[1] = (byte) (value >> 56);
        scratch[2] = (byte) (value >> 48);
        scratch[3] = (byte) (value >> 40);
        scratch[4] = (byte) (value >> 32);
        scratch[5] = (byte) (value >> 24);
        scratch[6] = (byte) (value >> 16);
        scratch[7] = (byte) (value >>  8);
        scratch[8] = (byte) (value      );
        digest.update(scratch, 0, 9);
    }

    private void start() {
        if (initialized) return;
        initialized = true;
        addTaggedLong(TAG_START, SEED);
    }

    public void addNull() {
        start();
        digest.update(TAG_NULL);
    }

    public void addByte(byte value) {
        start();
        scratch[0] = TAG_BYTE;
        scratch[1] = value;
        digest.update(scratch, 0, 2);
    }

    public void addInt(int value) {
        addTaggedInt(TAG_INT, value);
    }

    public void addLong(long value) {
        addTaggedLong(TAG_LONG, value);
    }

    public void addByteArray(byte[] value) {
        addTaggedInt(TAG_BYTES, value.length);
        digest.update(value);
    }

    public void addString(String value) {
        addTaggedInt(TAG_STRING, value.length());
        digest.update(Encodings.toBytes(value));
    }

    public void addInputStream(InputStream value) throws IOException {
        start();
        digest.update(TAG_STREAM);
        byte[] buffer = new byte[Util.BUFFER_SIZE];
        for (;;) {
            int rd = value.read(buffer);
            if (rd == -1) break;
            subDigest.update(buffer, 0, rd);
        }
        digest.update(subDigest.digest());
    }
    public void addInputStreamClosing(InputStream value) throws IOException {
        try {
            addInputStream(value);
        } finally {
            value.close();
        }
    }

    public byte[] finish() {
        start();
        digest.update(TAG_FINISH);
        initialized = false;
        return digest.digest();
    }
    public String finishEncoded() {
        return encodeDigest(finish());
    }

    public static RecordDigester getInstance() {
        try {
            return new RecordDigester();
        } catch (NoSuchAlgorithmException exc) {
            throw new RuntimeException(exc);
        }
    }

    public static String encodeDigest(byte[] digest) {
        return Encodings.toBase64(digest, false);
    }

}
