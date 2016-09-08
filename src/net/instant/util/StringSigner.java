package net.instant.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class StringSigner {

    private final String ALGORITHM = "HmacSHA1";

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
            return new StringSigner(key);
        } catch (Exception exc) {
            exc.printStackTrace();
            return null;
        }
    }
    public static StringSigner getInstance(File f) {
        try {
            return new StringSigner(f);
        } catch (Exception exc) {
            exc.printStackTrace();
            return null;
        }
    }

}
