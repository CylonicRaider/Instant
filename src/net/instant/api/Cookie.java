package net.instant.api;

import java.util.Map;
import org.json.JSONObject;

/**
 * An HTTP cookie.
 * The mapping part of the interface handles cookie attributes (such as
 * Path or Secure); to insert an attribute without an explicit value (such
 * as Secure), map the name to a null value. Although not part of the
 * interface, cookies maintain insertion order for attributes.
 */
public interface Cookie extends Map<String, String> {

    /**
     * A (deep) copy of this cookie.
     * Should be used to duplicate cookies received from requests if one
     * wants to modify them.
     */
    Cookie copy();

    /**
     * The name of the cookie.
     * Fixed upon creation.
     */
    String getName();

    /**
     * The value of the cookie.
     */
    String getValue();
    void setValue(String value);

    /**
     * Accessor methods for cookie attributes.
     * setAttribute() is similar to put() in the Map interface, but takes an
     * arbitrary object as the value to set and tries to convert it to a
     * string before actually put()ting it (raising a ClassCastException if it
     * cannot be converted); like put(), setAttribute() returns the old
     * (String) value of the attribute, or null if none.
     * getAttribute() and removeAttribute() are thin wrappers around get() and
     * remove(), respectively, and are provided for symmetry to
     * setAttribute().
     * At least the following classes can be converted:
     * - A null reference is kept as is;
     * - Strings are kept as is, too;
     * - Instances of java.lang.Number are converted to strings using their
     *   toString() method;
     * - Instances of java.util.Date and java.util.Calendar are formatted
     *   in a way suitable for use as the value of the Expires cookie
     *   attribute.
     */
    String getAttribute(String key);
    String setAttribute(String key, Object value);
    String removeAttribute(String key);

    /**
     * Amend this cookie with attribute from the variadic arguments.
     * pairs must have an even amount of entries, with even-indexed entries
     * being String keys and entries at odd-indexed entries being values
     * (suitable as second setAttribute() arguments) corresponding to the
     * respectively immediately preceding key. withAttributes() returns this
     * Cookie instance.
     */
    void updateAttributes(Object... pairs);
    Cookie withAttributes(Object... pairs);

    /**
     * Data stored inside the cookie.
     * Serialized and deserialized from/to the value on the fly;
     * authenticated by the core.
     * If this is non-null, the string value of this Cookie "tracks" the data
     * (as if the value were updated every time the data change). Vice versa,
     * if the value is set to an authentic serialized data object, this
     * property is set to a (new) JSONObject representing the data, and if the
     * value is set to something else, this property becomes null.
     * To push extensibility, interoperability, and forward compatibility, the
     * API does not define means to authenticate cookie data in custom
     * formats. If you *need* to encode opaque data, encode them into a string
     * and map a meaningful name in this property to them.
     */
    JSONObject getData();
    void setData(JSONObject data);

}
