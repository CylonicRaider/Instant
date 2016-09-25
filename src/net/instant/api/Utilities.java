package net.instant.api;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * Miscellaneous static utility methods.
 */
public final class Utilities {

    /* Regular expression pattern for escapeStringJS(). */
    private static final Pattern ESCAPE = Pattern.compile(
        "[^ !#-&(-\\[\\]-~]");

    /* Prevent (unintended) construction */
    private Utilities() {}

    /**
     * Escape the given string for inclusion into JavaScript source code.
     * For more complex objects, org.json tools can be used (which escape line
     * terminators properly).
     * If full is true, the string produced is surrounded by (double) quotes
     * and a null value is mapped to a "null" without quotes; if full is
     * false, the result can be embedded into JS string literals (whether
     * single- or double-quoted), and null data cause the method to crash.
     * Escaping happens defensively, encoding everything that is not a
     * printable ASCII character without special interpretation.
     */
    public static String escapeStringJS(String data, boolean full) {
        if (full && data == null) return "null";
        Matcher m = ESCAPE.matcher(data);
        // How old is *that* method to require a specific class?
        StringBuffer sb = new StringBuffer();
        if (full) sb.append('"');
        while (m.find()) {
            int ch = m.group().charAt(0);
            String repl = String.format((ch < 256) ? "\\x%02x" : "\\u%04x",
                                        ch);
            m.appendReplacement(sb, repl);
        }
        m.appendTail(sb);
        if (full) sb.append('"');
        return sb.toString();
    }

    /**
     * Construct a JSONObject from the given key-value pairs.
     * The variadic argument array consists of pairs of strings and arbitrary
     * objects (in that order), which are added to the newly-made object. To
     * create objects containing others, you have (obviously) to place an
     * invocation of this method instead of the value.
     */
    public static JSONObject createJSONObject(Object... params) {
        if (params.length % 2 == 1)
            throw new IllegalArgumentException("Invalid parameter amount " +
                "for createObject()");
        JSONObject ret = new JSONObject();
        for (int i = 0; i < params.length; i += 2) {
            if (! (params[i] instanceof String))
                throw new IllegalArgumentException("Invalid parameter " +
                    "type for createObject()");
            ret.put((String) params[i], params[i + 1]);
        }
        return ret;
    }

    /**
     * Merge two JSONObject-s in-place.
     * base is amended by key-value pairs from add; values from add override
     * those in base; if both base and add have the same key mapped to a
     * JSONObject, the values are merged recursively.
     */
    public static void mergeJSONObjects(JSONObject base, JSONObject add) {
        // Whoever made that API was a jerk.
        Iterator<String> keys = add.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (base.has(k) && base.get(k) instanceof JSONObject &&
                    add.get(k) instanceof JSONObject) {
                mergeJSONObjects((JSONObject) base.get(k),
                                 (JSONObject) add.get(k));
            } else {
                base.put(k, add.get(k));
            }
        }
    }

}
