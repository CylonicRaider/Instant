package net.instant.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import net.instant.api.Utilities;
import org.json.JSONObject;

public final class Util {

    public static final int BUFFER_SIZE = 16384;

    private static final SecureRandom RNG = new SecureRandom();

    private Util() {}

    public static byte[] getRandomness(int len) {
        byte[] buf = new byte[len];
        RNG.nextBytes(buf);
        return buf;
    }
    public static void clear(byte[] arr) {
        for (int i = 0; i < arr.length; i++) arr[i] = 0;
    }

    public static boolean matchWhitelist(String probe, List<Pattern> list) {
        for (Pattern p : list) {
            if (p.matcher(probe).matches()) return true;
        }
        return false;
    }

    public static String escapeStringJS(String data, boolean full) {
        return Utilities.escapeStringJS(data, full);
    }

    public static String trimQuery(String fullPath) {
        return Utilities.trimQuery(fullPath);
    }

    public static JSONObject createJSONObject(Object... params) {
        return Utilities.createJSONObject(params);
    }

    public static void mergeJSONObjects(JSONObject base, JSONObject add) {
        Utilities.mergeJSONObjects(base, add);
    }

    public static <E> Iterator<E> concat(final Iterator<E> a,
                                         final Iterator<E> b) {
        return new Iterator<E>() {

            private Iterator<E> it = a;
            private boolean atB = false;

            public boolean hasNext() {
                if (it.hasNext()) {
                    return true;
                } else if (! atB) {
                    it = b;
                    atB = true;
                    return true;
                } else {
                    return false;
                }
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
    public static <E> Iterable<E> concat(final Iterable<E> a,
                                         final Iterable<E> b) {
        return new Iterable<E>() {
            public Iterator<E> iterator() {
                return concat(a.iterator(), b.iterator());
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

    public static byte[] extractBytes(ByteBuffer buf) {
        byte[] ret = new byte[buf.limit()];
        buf.get(ret);
        return ret;
    }

    public static boolean nonempty(String input) {
        return Utilities.nonempty(input);
    }

    public static boolean isTrue(String input) {
        return Utilities.isTrue(input);
    }

    public static String getConfiguration(String propName, boolean ex) {
        String ret = System.getProperty(propName);
        if (ret == null)
            ret = System.getenv(
                propName.toUpperCase().replace(".", "_"));
        if (! ex && ret != null && ret.isEmpty())
            ret = null;
        return ret;
    }
    public static String getConfiguration(String propName) {
        return getConfiguration(propName, false);
    }

}
