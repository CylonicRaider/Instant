package net.instant.console;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import net.instant.InstantRunner;
import net.instant.Main;
import net.instant.console.util.Util;

public class BackendConsoleManager implements BackendConsoleManagerMXBean {

    public static String HELP_STRING = "\"console\" is bound to the " +
            "console object; \"main\" is bound to the Instant Main object; " +
            "\"instant\" is bound to the Instant API.\n" +
        "Use \"console.close()\" to exit.\n" +
        "Use \"for (var prop in X) println(prop);\" to list the properties " +
            "available on X, substituting \"this\" to list those on the " +
            "global object.";

    private final Main main;
    private final InstantRunner runner;
    private final Map<Integer, BackendConsole> consoles;
    private final Map<String, Object> defaultVariables;
    private final ObjectName objName;
    private int nextID;
    private boolean closed;
    private MBeanServer server;

    public BackendConsoleManager(Main main, InstantRunner runner) {
        if (runner == null && main != null) runner = main.getRunner();
        this.main = main;
        this.runner = runner;
        this.consoles = new HashMap<Integer, BackendConsole>();
        this.defaultVariables = new HashMap<String, Object>();
        this.objName = Util.classObjectName(BackendConsoleManager.class);
        this.nextID = 1;
        this.closed = false;
        this.server = null;
        initDefaultVariables();
    }

    public Main getMain() {
        return main;
    }

    public InstantRunner getRunner() {
        return runner;
    }

    public Map<String, Object> getDefaultVariables() {
        return defaultVariables;
    }

    public ObjectName getObjectName() {
        return objName;
    }

    public MBeanServer getInstalledServer() {
        return server;
    }

    public void install(MBeanServer server) {
        if (server == null) return;
        synchronized (this) {
            if (this.server != null)
                throw new IllegalStateException("Backend console manager " +
                    "is already registered in an MBean server");
            this.server = server;
        }
        try {
            server.registerMBean(this, objName);
        } catch (JMException exc) {
            throw new RuntimeException(exc);
        }
    }
    public void installPlatform() {
        install(ManagementFactory.getPlatformMBeanServer());
    }

    public synchronized int[] listConsoles() {
        Integer[] result = consoles.keySet().toArray(
            new Integer[consoles.size()]);
        int[] ret = new int[result.length];
        for (int i = 0; i < result.length; i++) ret[i] = result[i];
        return ret;
    }

    public synchronized BackendConsole getConsole(int id) {
        return consoles.get(id);
    }

    public BackendConsole newConsole() {
        BackendConsole ret;
        synchronized (this) {
            if (closed)
                throw new IllegalStateException("Cannot create consoles " +
                                                "when closed");
            int id = nextID++;
            ret = new BackendConsole(this, id);
            consoles.put(id, ret);
        }
        ret.install(server);
        return ret;
    }

    protected synchronized void remove(BackendConsole console) {
        consoles.remove(console.getID());
    }

    public void close() {
        MBeanServer server;
        synchronized (this) {
            this.closed = true;
            server = this.server;
            this.server = null;
            BackendConsole[] cleanup = consoles.values().toArray(
                new BackendConsole[consoles.size()]);
            for (BackendConsole cons : cleanup) {
                cons.close();
            }
        }
        if (server != null) {
            try {
                server.unregisterMBean(objName);
            } catch (JMException exc) {
                throw new RuntimeException(exc);
            }
        }
    }

    public void exportOn(InetSocketAddress addr, Map<String, Object> env)
            throws IOException {
        MBeanServer srv = getInstalledServer();
        if (srv == null) return;
        Util.exportMBeanServerRMI(srv, addr, addr, env);
    }

    protected void initDefaultVariables() {
        defaultVariables.put("main", main);
        defaultVariables.put("instant", runner);
        defaultVariables.put("help", HELP_STRING);
    }

    public static BackendConsoleManager makeDefault(InstantRunner runner) {
        return new BackendConsoleManager(null, runner);
    }
    public static BackendConsoleManager makeDefault(Main main) {
        return new BackendConsoleManager(main, main.getRunner());
    }

    public static void exportPlatformManagement(InetSocketAddress addr,
            Map<String, Object> env) throws IOException {
        MBeanServer srv = ManagementFactory.getPlatformMBeanServer();
        Util.exportMBeanServerRMI(srv, addr, addr, env);
    }

}
