package net.instant.api;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Miscellaneous static utility methods.
 */
public final class Utilities {

    /* Regular expression pattern for escapeStringJS(). */
    private static final Pattern ESCAPE = Pattern.compile(
        "[^ !#-&(-\\[\\]-~]");

    /* Regular expression for splitQuery(). */
    private static final Pattern QUERY = Pattern.compile(
        "([^?]*)(?:\\?(.*))?");

    /* Prevent (unintended) construction */
    private Utilities() {}

    /**
     * Return a Calendar object whose given field has been incremented by
     * adjust relative to the time of the method call.
     * This is a shortcut for the following:
     *     Calendar cal = Calendar.getInstance();
     *     cal.add(field, adjust);
     *     return cal;
     */
    public static Calendar calendarIn(int field, int adjust) {
        Calendar cal = Calendar.getInstance();
        cal.add(field, adjust);
        return cal;
    }

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
        StringBuffer sb = new StringBuffer();
        if (full) sb.append('"');
        while (m.find()) {
            int ch = m.group().charAt(0);
            m.appendReplacement(sb, String.format((ch < 256) ? "\\\\x%02x" :
                "\\\\u%04x", ch));
        }
        m.appendTail(sb);
        if (full) sb.append('"');
        return sb.toString();
    }

    /**
     * Split the given path into the path proper and the query string.
     * The return value is an array with two elements, the path (never null
     * but may be empty) and the query string (null signifies that fullPath
     * contained no question marks while an empty value means an empty query
     * string).
     */
    public static String[] splitQueryString(String fullPath) {
        Matcher m = QUERY.matcher(fullPath);
        if (! m.matches()) {
            // Should not happen.
            throw new RuntimeException("Could not match request path?!");
        }
        return new String[] { m.group(1), m.group(2) };
    }

    /**
     * Recombine the two values returned from splitQueryString().
     * path may not be null, and is assumed not to contain question marks;
     * if query is null, the return value is path; otherwise, the return
     * value is path + "?" + query.
     */
    public static String joinQueryString(String path, String query) {
        if (path == null) {
            throw new NullPointerException("joinQueryString() path may not " +
                "be null");
        } else if (query == null) {
            return path;
        } else {
            return path + '?' + query;
        }
    }

    /**
     * Parse the given query string into a Map.
     * A query of null is treated like an empty string. The order of first
     * appearance is preserved. If a query string entry has no "value" (i.e.
     * no equals sign), its entirety is used as the key and is mapped to a
     * value of null.
     */
    public static Map<String, String> parseQueryString(String query) {
        if (query == null) query = "";
        Map<String, String> ret = new LinkedHashMap<String, String>();
        for (String entry : query.split("&")) {
            String[] parts = entry.split("=", 2);
            String key, value;
            try {
                key = URLDecoder.decode(parts[0], "utf-8");
                if (parts.length == 1) {
                    value = null;
                } else {
                    value = URLDecoder.decode(parts[1], "utf-8");
                }
            } catch (UnsupportedEncodingException exc) {
                // Should not happen.
                throw new RuntimeException(exc);
            }
            ret.put(key, value);
        }
        return ret;
    }

    /**
     * Extract exactly one JSON value from the given string and return it.
     * Differently to the JSONObject constructor etc., this method does not
     * accept garbage after the end of the value and throws an exception if
     * such garbage is encountered.
     */
    public static Object parseOneJSONValue(String input)
            throws JSONException {
        JSONTokener tok = new JSONTokener(input);
        Object ret = tok.nextValue();
        if (tok.nextClean() != 0)
            throw tok.syntaxError("Unexpected garbage after JSON value");
        return ret;
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

    /**
     * Interpret the given string as a file or as a generic URL.
     * If the string contains a colon, it is interpreted as a URL; otherwise,
     * as a filesystem path (and it is converted to a URL). If the string is
     * the null reference or empty, a MalformedURLException is thrown.
     */
    public static URL makeURL(String path) throws MalformedURLException {
        if (! nonempty(path)) {
            throw new MalformedURLException("String is null or empty");
        } else if (path.contains(":")) {
            return new URL(path);
        } else {
            return new File(path).toURI().toURL();
        }
    }

    /**
     * Return whether the given string is not null and nonempty.
     */
    public static boolean nonempty(String s) {
        return (s != null && ! s.isEmpty());
    }

    /**
     * Return whether the string represents an affirmative value.
     * Intended to be more lenient than Boolean.parseBoolean(); accepts
     * inputs such as "1", "y", "yes", "on" (ignoring case) as true.
     */
    public static boolean isTrue(String s) {
        if (s == null) return false;
        return (Boolean.parseBoolean(s) || s.equalsIgnoreCase("1") ||
            s.equalsIgnoreCase("y") || s.equalsIgnoreCase("yes") ||
            s.equalsIgnoreCase("on"));
    }

}
