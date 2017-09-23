package net.instant.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class StringSigner {

    public static final String ALGORITHM = "HmacSHA1";
    public static final int KEYSIZE = 64;

    private final Mac impl;

    public StringSigner(byte[] key) throws InvalidKeyException,
             NoSuchAlgorithmException {
        impl = Mac.getInstance(ALGORITHM);
        impl.init(new SecretKeySpec(key, ALGORITHM));
    }
    public StringSigner(InputStream input) throws IOException,
            InvalidKeyException, NoSuchAlgorithmException {
        this(Util.extractBytes(Util.readInputStreamClosing(input)));
    }
    public StringSigner(File file) throws IOException,
            InvalidKeyException, NoSuchAlgorithmException {
        this(new FileInputStream(file));
    }

    public byte[] sign(byte[] data) {
        try {
            return ((Mac) impl.clone()).doFinal(data);
        } catch (CloneNotSupportedException exc) {
            return null;
        }
    }

    public boolean verify(byte[] data, byte[] signature) {
        byte[] sig = sign(data);
        if (sig == null) return false;
        return Arrays.equals(sig, signature);
    }

    public static StringSigner getInstance(byte[] key) {
        try {
            if (key == null) key = Util.getStrongRandomness(KEYSIZE);
            return new StringSigner(key);
        } catch (Exception exc) {
            throw new RuntimeException(exc);
        }
    }
    public static StringSigner getInstance(File f, boolean create) {
        try {
            // Assuming KEYSIZE is not zero.
            if (create && f.length() != KEYSIZE) {
                byte[] data = Util.getStrongRandomness(KEYSIZE);
                FileOutputStream os = new FileOutputStream(f);
                Util.writeOutputStreamClosing(os, ByteBuffer.wrap(data));
                return new StringSigner(data);
            } else {
                return new StringSigner(f);
            }
        } catch (Exception exc) {
            throw new RuntimeException(exc);
        }
    }

}
