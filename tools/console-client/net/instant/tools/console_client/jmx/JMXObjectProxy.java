package net.instant.tools.console_client.jmx;

import java.io.IOException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

public abstract class JMXObjectProxy {

    private static final String[] EMPTY_PARAMS = {};
    private static final Object[] EMPTY_ARGS = {};

    private final MBeanServerConnection connection;
    private final ObjectName objectName;

    public JMXObjectProxy(MBeanServerConnection connection,
                          ObjectName objectName) {
        this.connection = connection;
        this.objectName = objectName;
    }

    public MBeanServerConnection getConnection() {
        return connection;
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    protected <T> T invokeMethod(String methodName, Object[] args,
            String[] params, Class<T> returnType) throws RuntimeException {
        if (args == null) args = EMPTY_ARGS;
        if (params == null) params = EMPTY_PARAMS;
        Object result;
        try {
            result = connection.invoke(objectName, methodName, args, params);
        } catch (InstanceNotFoundException exc) {
            throw new RuntimeException(exc);
        } catch (MBeanException exc) {
            throw new RuntimeException(exc);
        } catch (ReflectionException exc) {
            throw new RuntimeException(exc);
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
        T ret;
        try {
            ret = returnType.cast(result);
        } catch (ClassCastException exc) {
            // A ReflectionException would be more appropriate, but we already
            // swallow exceptions.
            throw new RuntimeException("Method " + methodName + " on " +
                objectName + " returned invalid type " +
                getClassName(result) + " instead of " + returnType.getName(),
                exc);
        }
        return ret;
    }

    protected static String getClassName(Object obj) {
        if (obj == null) return "<null>";
        return obj.getClass().getName();
    }

    protected static ObjectName createObjectName(String base) {
        try {
            return new ObjectName(base);
        } catch (MalformedObjectNameException exc) {
            throw new RuntimeException(exc);
        }
    }

}
