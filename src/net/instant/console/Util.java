package net.instant.console;

import java.util.Hashtable;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public final class Util {

    private Util() {}

    public static String[] splitClassName(String name) {
        int idx = name.lastIndexOf('.');
        String pkg, cls;
        if (idx == -1) {
            return new String[] { "", name };
        } else {
            return new String[] {
                name.substring(0, idx),
                name.substring(idx + 1)
            };
        }
    }

    public static ObjectName classObjectName(Class<?> cls, String... props) {
        if (props != null && props.length % 2 != 0)
            throw new IllegalArgumentException("There must be an even " +
                "amount of properties");
        String[] parts = splitClassName(cls.getName());
        Hashtable<String, String> table = new Hashtable<String, String>();
        table.put("type", parts[1]);
        if (props != null) {
            for (int i = 0; i < props.length; i += 2) {
                table.put(props[i], props[i + 1]);
            }
        }
        try {
            return ObjectName.getInstance(parts[0], table);
        } catch (MalformedObjectNameException exc) {
            throw new RuntimeException(exc);
        }
    }

}
