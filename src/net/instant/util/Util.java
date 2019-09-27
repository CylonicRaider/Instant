package net.instant.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import net.instant.api.Utilities;
import net.instant.util.config.Configuration;
import org.json.JSONException;
import org.json.JSONObject;

public final class Util {

    public static final int BUFFER_SIZE = 65536;

    private static final SecureRandom RNG = new SecureRandom();
    private static final boolean useFastRNG;

    static {
        useFastRNG = isTrue(Configuration.DEFAULT.get(
            "instant.rng._unsafeFast"));
    }

    private Util() {}

    public static byte[] getRandomness(int len) {
        byte[] buf = new byte[len];
        RNG.nextBytes(buf);
        return buf;
    }
    public static byte[] getStrongRandomness(int len) {
        if (useFastRNG) {
            return getRandomness(len);
        } else {
            return RNG.generateSeed(len);
        }
    }
    public static void clear(byte[] arr) {
        Arrays.fill(arr, (byte) 0);
    }

    public static boolean matchWhitelist(String probe, List<Pattern> list) {
        for (Pattern p : list) {
            if (p.matcher(probe).matches()) return true;
        }
        return false;
    }

    public static Calendar calendarIn(int field, int adjust) {
        return Utilities.calendarIn(field, adjust);
    }

    public static String escapeStringJS(String data, boolean full) {
        return Utilities.escapeStringJS(data, full);
    }

    public static String[] splitQueryString(String fullPath) {
        return Utilities.splitQueryString(fullPath);
    }

    public static String joinQueryString(String path, String query) {
        return Utilities.joinQueryString(path, query);
    }

    public static Object parseOneJSONValue(String input)
            throws JSONException {
        return Utilities.parseOneJSONValue(input);
    }

    public static JSONObject createJSONObject(Object... params) {
        return Utilities.createJSONObject(params);
    }

    public static void mergeJSONObjects(JSONObject base, JSONObject add) {
        Utilities.mergeJSONObjects(base, add);
    }

    @SafeVarargs
    public static <E> Iterator<E> concat(final Iterator<E>... l) {
        if (l.length == 0) return Collections.<E>emptyList().iterator();
        return new Iterator<E>() {

            private Iterator<E> it = l[0];
            private int idx = 1;

            public boolean hasNext() {
                if (it.hasNext()) return true;
                while (idx < l.length) {
                    it = l[idx++];
                    if (it.hasNext()) return true;
                }
                return false;
            }

            public E next() {
                // Swap out iterator if necessary.
                hasNext();
                return it.next();
            }

            public void remove() {
                it.remove();
            }

        };
    }
    @SafeVarargs
    public static <E> Iterable<E> concat(final Iterable<E>... l) {
        return new Iterable<E>() {
            public Iterator<E> iterator() {
                @SuppressWarnings("unchecked")
                Iterator<E>[] il = (Iterator<E>[]) new Iterator<?>[l.length];
                for (int i = 0; i < l.length; i++) il[i] = l[i].iterator();
                return concat(il);
            }
        };
    }

    public static ByteBuffer readInputStream(InputStream input)
            throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        int idx = 0;
        for (;;) {
            int rd = input.read(buf, idx, buf.length - idx);
            if (rd < 0) break;
            idx += rd;
            if (idx == buf.length) {
                byte[] nbuf = new byte[idx * 2];
                System.arraycopy(buf, 0, nbuf, 0, idx);
                buf = nbuf;
            }
        }
        return ByteBuffer.wrap(buf, 0, idx);
    }
    public static ByteBuffer readInputStreamClosing(InputStream input)
            throws IOException {
        try {
            return readInputStream(input);
        } finally {
            input.close();
        }
    }

    public static void writeOutputStream(OutputStream output,
            ByteBuffer data) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        for (;;) {
            int rem = data.remaining();
            if (rem == 0) break;
            if (rem > buf.length) rem = buf.length;
            data.get(buf, 0, rem);
            output.write(buf, 0, rem);
        }
    }
    public static void writeOutputStreamClosing(OutputStream output,
            ByteBuffer data) throws IOException {
        try {
            writeOutputStream(output, data);
        } finally {
            output.close();
        }
    }

    // Use ByteBuffer.wrap() to reverse.
    public static byte[] extractBytes(ByteBuffer buf) {
        byte[] ret = new byte[buf.limit()];
        buf.get(ret);
        return ret;
    }

    public static int countChars(String input, int ch) {
        int ret = 0;
        for (int i = 0, l = input.length(); i < l; i++) {
            if (input.charAt(i) == ch) ret++;
        }
        return ret;
    }

    public static URL makeURL(String path) throws MalformedURLException {
        return Utilities.makeURL(path);
    }

    public static boolean nonempty(String input) {
        return Utilities.nonempty(input);
    }

    public static boolean isTrue(String input) {
        return Utilities.isTrue(input);
    }

}
