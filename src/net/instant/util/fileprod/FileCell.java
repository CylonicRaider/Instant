package net.instant.util.fileprod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import net.instant.util.Encodings;
import net.instant.util.Util;

public class FileCell {

    private final String name;
    private final ByteBuffer content;
    private final long created;
    private String etag;

    public FileCell(String name, ByteBuffer content, long created) {
        this.name = name;
        this.content = content;
        this.created = created;
        this.etag = null;
    }
    public FileCell(String name, InputStream input, long created)
            throws IOException {
        this(name, Util.readInputStreamClosing(input), created);
    }

    public String getName() {
        return name;
    }
    protected ByteBuffer getRawData() {
        return content;
    }
    public ByteBuffer getData() {
        return (content != null) ? content.asReadOnlyBuffer() : null;
    }
    public long getCreated() {
        return created;
    }

    public String getETag() {
        if (etag == null) {
            MessageDigest d;
            try {
                d = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException exc) {
                // Seriously?
                return null;
            }
            d.update(Encodings.toBytes(created));
            if (content == null) {
                d.update((byte) 0);
            } else {
                d.update((byte) 1);
                d.update(getData());
            }
            etag = Encodings.toHex(d.digest());
        }
        return etag;
    }

    public int getSize() {
        return (content != null) ? content.limit() : -1;
    }

    public boolean isValid() {
        return true;
    }

}
