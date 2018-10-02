package net.instant.util;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordHasher {

    public static final String ALGORITHM = "PBKDF2WithHmacSHA1";
    public static final int KEY_SIZE = 32; // in bytes
    public static final int ITERATIONS = 1 << 12;

    private final SecretKeyFactory impl;

    public PasswordHasher() throws NoSuchAlgorithmException {
        impl = SecretKeyFactory.getInstance(ALGORITHM);
    }

    public String hash(byte[] salt, char[] password) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS,
                                         KEY_SIZE * 8);
        try {
            byte[] rawHash = impl.generateSecret(spec).getEncoded();
            return toMB64(salt) + "$" + toMB64(rawHash);
        } catch (InvalidKeySpecException exc) {
            throw new RuntimeException(exc);
        } finally {
            spec.clearPassword();
        }
    }

    public byte[] newSalt() {
        return Util.getRandomness(KEY_SIZE);
    }

    public String hash(char[] password) {
        return hash(newSalt(), password);
    }

    public boolean verify(char[] password, String hash)
            throws IllegalArgumentException {
        if (Util.countChars(hash, '$') != 1)
            throw new IllegalArgumentException("Malformed password hash");
        String saltString = hash.substring(0, hash.indexOf('$'));
        String pwHash = hash(fromMB64(saltString), password);
        if (hash.length() != pwHash.length()) return false;
        // Avoid leaking the position of the first difference.
        boolean ok = true;
        for (int i = 0; i < pwHash.length(); i++) {
            if (hash.charAt(i) != pwHash.charAt(i)) ok = false;
        }
        return ok;
    }

    public static PasswordHasher getInstance() {
        try {
            return new PasswordHasher();
        } catch (NoSuchAlgorithmException exc) {
            throw new RuntimeException(exc);
        }
    }

    public static void clear(char[] data) {
        Arrays.fill(data, '\0');
    }

    private static String toMB64(byte[] data) {
        String ret = Encodings.toBase64(data).replace('+', '.');
        int idx = ret.indexOf('=');
        if (idx >= 0) {
            return ret.substring(0, idx);
        } else {
            return ret;
        }
    }
    private static byte[] fromMB64(String input) {
        String pad;
        switch (input.length() % 4) {
            case 0:
                pad = "";
                break;
            case 2:
                pad = "==";
                break;
            case 3:
                pad = "=";
                break;
            default:
                throw new IllegalArgumentException("Invalid password hash " +
                    "component");
        }
        return Encodings.fromBase64(input.replace('.', '+').concat(pad));
    }

}
