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
     * Amend this cookie with attribute from the variadic arguments.
     * pairs must have an even amount of entries, with entries at even-indexed
     * entries being keys and entries at odd-indexed entries being values
     * corresponding to the respectively immediately preceding key.
     * withAttributes() returns this Cookie instance.
     */
    void updateAttributes(String... pairs);
    Cookie withAttributes(String... pairs);

    /**
     * Data stored inside the cookie.
     * Serialized and deserialized from/to the value on the fly;
     * authenticated by the core. To push extensibility, interoperability,
     * and forward compatibility, the API does not define means to
     * authenticate cookie data in custom formats. If you *need* to
     * encode opaque data, encode them into a string and map a meaningful
     * name to them in data.
     */
    JSONObject getData();
    void setData(JSONObject data);

}
