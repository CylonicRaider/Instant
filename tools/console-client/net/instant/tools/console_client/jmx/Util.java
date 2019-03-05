package net.instant.tools.console_client.jmx;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class Util {

    private static final Pattern INET_ADDRESS = Pattern.compile(
        "(\\[[0-9a-fA-F.:]+\\]|[a-zA-Z0-9.-]+):([0-9]+)");

    private Util() {}

    public static InetSocketAddress parseAddressOrNull(String input) {
        Matcher m = INET_ADDRESS.matcher(input);
        if (! m.matches()) return null;
        int port;
        try {
            port = Integer.parseInt(m.group(2));
        } catch (NumberFormatException exc) {
            return null;
        }
        return new InetSocketAddress(m.group(1), port);
    }

    public static JMXServiceURL serviceURLForAddress(InetSocketAddress addr) {
        // Mirroring the code from net.instant.console.util.Util.
        String endpoint = addr.getAddress().getHostName() + ":" +
            addr.getPort();
        try {
            return new JMXServiceURL("service:jmx:rmi://" + endpoint +
                "/jndi/rmi://" + endpoint + "/jmxrmi");
        } catch (MalformedURLException exc) {
            // *Should* not happen.
            throw new RuntimeException(exc);
        }
    }

    public static JMXServiceURL parseServiceURL(String input)
            throws MalformedURLException {
        InetSocketAddress addr = parseAddressOrNull(input);
        if (addr != null) return serviceURLForAddress(addr);
        return new JMXServiceURL(input);
    }

    public static JMXConnector connectJMX(String endpoint,
            Map<String, Object> env) throws IOException {
        JMXServiceURL url = parseServiceURL(endpoint);
        return JMXConnectorFactory.connect(url, env);
    }

    public static Map<String, Object> prepareCredentials(String username,
                                                         String password) {
        Map<String, Object> ret = new HashMap<String, Object>();
        ret.put(JMXConnector.CREDENTIALS, new String[] { username,
                                                         password });
        return ret;
    }

}
