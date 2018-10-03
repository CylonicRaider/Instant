package net.instant.util;

import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordHasher {

    public static final String ALGORITHM = "PBKDF2WithHmacSHA1";
    public static final int KEY_SIZE = 32; // in bytes
    public static final int ITERATIONS = 1 << 16;

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
        if (data != null) Arrays.fill(data, '\0');
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

    private static char[] resizeBuffer(char[] buf, int oldSize, int newSize) {
        char[] newbuf = new char[newSize];
        System.arraycopy(buf, 0, newbuf, 0, oldSize);
        clear(buf);
        return newbuf;
    }

    public static char[] readPassword(Reader rd) {
        char[] buf = new char[128];
        int buflen = 0;
        for (;;) {
            int ch;
            try {
                ch = rd.read();
                if (ch == -1 || ch == '\n' || ch == '\r') {
                    break;
                } else if (buflen == buf.length) {
                    buf = resizeBuffer(buf, buflen, 2 * buflen);
                }
                buf[buflen++] = (char) ch;
            } catch (IOException exc) {
                throw new RuntimeException(exc);
            } finally {
                ch = 0;
            }
        }
        if (buflen != buf.length) buf = resizeBuffer(buf, buflen, buflen);
        return buf;
    }

    /* Run as a stand-alone program to generate hashes */
    public static void main(String[] args) {
        Console cons = System.console();
        char[] pw;
        if (cons != null) {
            pw = cons.readPassword("Password: ");
        } else {
            pw = readPassword(new InputStreamReader(System.in));
        }
        if (pw == null) {
            System.err.println("ERROR: No password given");
            System.exit(1);
        }
        try {
            System.out.println(getInstance().hash(pw));
        } finally {
            clear(pw);
        }
    }

}
