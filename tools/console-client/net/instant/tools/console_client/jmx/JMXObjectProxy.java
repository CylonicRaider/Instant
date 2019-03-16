package net.instant.tools.console_client.jmx;

import java.io.IOException;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
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

    protected <T> T getAttributeEx(String name, Class<T> expectedType)
            throws MBeanException, AttributeNotFoundException,
                   InstanceNotFoundException, ReflectionException,
                   IOException {
        Object result = connection.getAttribute(objectName, name);
        T ret;
        try {
            ret = expectedType.cast(result);
        } catch (ClassCastException exc) {
            throw new ReflectionException(exc, "Attribute " + name + " of " +
                objectName + " was of type " + getClassName(result) +
                " instead of " + expectedType.getName());
        }
        return ret;
    }

    protected <T> T getAttribute(String name, Class<T> expectedType) {
        try {
            return getAttributeEx(name, expectedType);
        } catch (MBeanException exc) {
            throw new RuntimeException(exc);
        } catch (AttributeNotFoundException exc) {
            throw new RuntimeException(exc);
        } catch (InstanceNotFoundException exc) {
            throw new RuntimeException(exc);
        } catch (ReflectionException exc) {
            throw new RuntimeException(exc);
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
    }

    protected <T> T invokeMethodEx(String methodName, Object[] args,
                                   String[] params, Class<T> returnType)
            throws InstanceNotFoundException, MBeanException,
                   ReflectionException, IOException {
        if (args == null) args = EMPTY_ARGS;
        if (params == null) params = EMPTY_PARAMS;
        Object result = connection.invoke(objectName, methodName, args,
                                          params);
        T ret;
        try {
            ret = returnType.cast(result);
        } catch (ClassCastException exc) {
            throw new ReflectionException(exc, "Method " + methodName +
                " on " + objectName + " returned invalid type " +
                getClassName(result) + " instead of " + returnType.getName());
        }
        return ret;
    }

    protected <T> T invokeMethod(String methodName, Object[] args,
                                 String[] params, Class<T> returnType) {
        try {
            return invokeMethodEx(methodName, args, params, returnType);
        } catch (InstanceNotFoundException exc) {
            throw new RuntimeException(exc);
        } catch (MBeanException exc) {
            throw new RuntimeException(exc);
        } catch (ReflectionException exc) {
            throw new RuntimeException(exc);
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
    }

    protected void listenNotificationType(String type,
                                          NotificationListener l,
                                          Object handback) {
        NotificationFilterSupport filter = new NotificationFilterSupport();
        filter.enableType(type);
        try {
            connection.addNotificationListener(objectName, l, filter,
                                               handback);
        } catch (InstanceNotFoundException exc) {
            throw new RuntimeException(exc);
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
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
