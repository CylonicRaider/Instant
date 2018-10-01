package net.instant.console.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.rmi.registry.LocateRegistry;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import net.instant.util.Formats;

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

    public static void exportMBeanServerRMI(MBeanServer mbsrv,
            InetSocketAddress endpoint, InetSocketAddress registry)
            throws IOException {
        // Algorithm taken from the RMI documentation on mimicking
        // out-of-the-box management.
        /* Global system configuration. :( */
        // Avoid creating predictable object ID-s.
        if (System.getProperty("java.rmi.server.randomIDs") == null)
            System.setProperty("java.rmi.server.randomIDs", "true");
        // Coerce RMI to actually use the hostname we feed it.
        if (System.getProperty("java.rmi.server.hostname") == null)
            System.setProperty("java.rmi.server.hostname",
                               endpoint.getHostName());
        /* Start the RMI registry. */
        // NOTE: The JDK implementation's policy is to allow remote accesses
        //       from any port of any address the local VM can bind to.
        LocateRegistry.createRegistry(registry.getPort(), null,
            new SingleAddressRMIServerSocketFactory(registry));
        /* Format the service URL. */
        String endpointStr = Formats.formatInetSocketAddress(endpoint, false);
        String registryStr = Formats.formatInetSocketAddress(registry, false);
        JMXServiceURL url;
        try {
            url = new JMXServiceURL("service:jmx:rmi://" + endpointStr +
                "/jndi/rmi://" + registryStr + "/jmxrmi");
        } catch (MalformedURLException exc) {
            // *Should* not happen.
            throw new RuntimeException(exc);
        }
        /* Configure an environment for the server. */
        Map<String, Object> env = new HashMap<String, Object>();
        env.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE,
                new SingleAddressRMIServerSocketFactory(endpoint));
        /* Start the server. */
        JMXConnectorServer jcsrv =
            JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbsrv);
        jcsrv.start();
    }

}
