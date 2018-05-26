package net.instant;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.instant.hooks.APIWebSocketHook;
import net.instant.plugins.PluginException;
import net.instant.util.Formats;
import net.instant.util.Logging;
import net.instant.util.argparse.ArgumentParser;
import net.instant.util.argparse.KeyValue;
import net.instant.util.argparse.ParseException;
import net.instant.util.argparse.ParseResult;
import net.instant.util.argparse.ValueArgument;
import net.instant.util.argparse.ValueOption;
import net.instant.util.fileprod.FSResourceProducer;
import net.instant.ws.InstantWebSocketServer;

public class Main implements Runnable {

    public static final String APPNAME = "Instant";
    public static final String VERSION = "1.5.3";
    public static final String FINE_VERSION;
    public static final String DESCRIPTION = "A Web-based threaded chat.";

    public static final String ROOM_RE =
        "[a-zA-Z](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?";
    public static final String STAGING_RE = "dev/[a-zA-Z0-9-]+";

    private static final String VERSION_FILE;
    private static final Logger LOGGER;

    static {
        Logging.initFormat();
        LOGGER = Logger.getLogger("Main");
        String v;
        InputStream stream = null;
        try {
            stream = new URL(Main.class.getResource(""),
                             "/META-INF/MANIFEST.MF").openStream();
            Manifest mf = new Manifest(stream);
            v = mf.getMainAttributes().getValue("X-Git-Commit");
        } catch (IOException exc) {
            v = null;
        } finally {
            try {
                if (stream != null) stream.close();
            } catch (IOException exc) {}
        }
        FINE_VERSION = v;
        VERSION_FILE = String.format("this._instantVersion_ = " +
            "{version: %s, revision: %s};\n",
            Formats.escapeJSString(VERSION, true),
            Formats.escapeJSString(FINE_VERSION, true));
    }

    private final String[] args;
    private InstantRunner runner;
    private String startupCmd;

    public Main(String[] args) {
        this.args = args;
        this.runner = new InstantRunner();
    }

    public int getArgumentCount() {
        return args.length;
    }

    public String getArgument(int i) {
        if (i < 0 || i > args.length)
            throw new IndexOutOfBoundsException("Invalid index " + i);
        return args[i];
    }

    public InstantRunner getRunner() {
        return runner;
    }
    public void setRunner(InstantRunner r) {
        runner = r;
    }

    protected ParseResult parseArguments(ArgumentParser p) {
        p.addStandardOptions();
        ValueOption<String> host = p.add(ValueOption.of(String.class,
            "host", 'h', "Host to bind to").defaultsTo("*"));
        ValueArgument<Integer> port = p.add(ValueArgument.of(Integer.class,
            "port", "Port to bind to").defaultsTo(8080));
        ValueOption<File> webroot = p.add(ValueOption.of(File.class,
            "webroot", 'r', "Path containing static directories")
            .defaultsTo(new File(".")));
        ValueOption<String> httpLog = p.add(ValueOption.of(String.class,
            "http-log", null, "Log file for HTTP requests").defaultsTo("-")
            .withPlaceholder("<path>"));
        ValueOption<String> debugLog = p.add(ValueOption.of(String.class,
            "debug-log", null, "Log file for debugging").defaultsTo("-")
            .withPlaceholder("<path>"));
        ValueOption<String> logLevel = p.add(ValueOption.of(String.class,
            "log-level", 'L', "Logging level").defaultsTo("INFO"));
        ValueOption<List<File>> plugPath = p.add(ValueOption.ofList(
            File.class, "plugin-path", 'P', "Path to search for plugins in")
            .defaultsTo(new ArrayList<File>()));
        ValueOption<List<String>> plugList = p.add(ValueOption.ofList(
            String.class, "plugins", 'p', "List of plugins to load")
            .defaultsTo(new ArrayList<String>()));
        ValueOption<String> cmd = p.add(ValueOption.of(String.class,
            "startup-cmd", 'c', "OS command to run before entering " +
            "main loop"));
        ValueOption<List<KeyValue>> options = p.add(ValueOption.ofAccum(
            KeyValue.class, "option", 'o', "Additional configuration " +
            "parameters"));
        ValueOption<File> config = p.add(ValueOption.of(File.class,
            "config", 'C', "Configuration file"));
        ParseResult r = parseArgumentsInner(p);
        String hostval = r.get(host);
        if (hostval.equals("*")) hostval = null;
        runner.setHost(hostval);
        runner.setPort(r.get(port));
        runner.setWebroot(r.get(webroot));
        runner.setHTTPLog(resolveLogFile(r.get(httpLog)));
        runner.makeConfig().putAll(r.get(options));
        File configPath = r.get(config);
        if (configPath != null) runner.addConfigFile(configPath);
        Logging.redirectToStream(resolveLogFile(r.get(debugLog)));
        Logging.setLevel(Level.parse(r.get(logLevel)));
        for (File f : r.get(plugPath)) runner.addPluginPath(f);
        for (String s : r.get(plugList)) runner.addPlugin(s);
        startupCmd = r.get(cmd);
        return r;
    }
    protected ParseResult parseArgumentsInner(ArgumentParser p) {
        try {
            return p.parse(args);
        } catch (ParseException exc) {
            System.err.println(exc.getMessage());
            System.exit(1);
            return null;
        }
    }

    public void run() {
        Logging.captureExceptions(LOGGER);
        String version = VERSION;
        if (FINE_VERSION != null) version += " (" + FINE_VERSION + ")";
        parseArguments(new ArgumentParser(APPNAME, version, DESCRIPTION));
        LOGGER.info(APPNAME + " " + version);
        runner.addFileAlias(Pattern.compile("/(\\?.*)?"),
                            "/pages/main.html");
        runner.addFileAlias(Pattern.compile("/favicon.ico(\\?.*)?"),
                            "/static/logo-static_128x128.ico");
        runner.addFileAlias(Pattern.compile("/([^/]+\\.html)(\\?.*)?"),
                            "/pages/\\1");
        runner.addFileAlias(Pattern.compile("/room/" + ROOM_RE + "/(\\?.*)?"),
                            "/static/room.html");
        runner.addFileAlias(Pattern.compile("/(" + STAGING_RE + ")/(\\?.*)?"),
                            "/static/\\1/main.html");
        runner.addFileAlias(Pattern.compile("/(" + STAGING_RE + ")/room/" +
                            ROOM_RE + "/(\\?.*)?"),
                            "/static/\\1/room.html");
        runner.addContentType(".*\\.html", "text/html; charset=utf-8");
        runner.addContentType(".*\\.css", "text/css; charset=utf-8");
        runner.addContentType(".*\\.js", "application/javascript; " +
                              "charset=utf-8");
        runner.addContentType(".*\\.svg", "image/svg+xml; charset=utf-8");
        runner.addContentType(".*\\.png", "image/png");
        runner.addContentType(".*\\.jpg", "image/jpeg");
        runner.addContentType(".*\\.ico", "image/vnd.microsoft.icon");
        runner.addContentType(".*\\.txt", "text/plain; charset=utf-8");
        runner.addRedirect(Pattern.compile("/(room/" + ROOM_RE + ")(\\?.*)?"),
                           "/\\1/\\2", 301);
        runner.addRedirect(Pattern.compile("/(" + STAGING_RE + ")(\\?.*)?"),
                           "/\\1/\\2", 301);
        runner.addRedirect(Pattern.compile("/(" + STAGING_RE + "/room/" +
                           ROOM_RE + ")(\\?.*)?"),
                           "\\1/\\2", 301);
        runner.addSyntheticFile(InstantRunner.VERSION_FILE, VERSION_FILE);
        FSResourceProducer prod = runner.makeSourceFiles();
        prod.whitelist("/pages/.*");
        prod.whitelist("/static/.*");
        APIWebSocketHook ws = runner.makeAPIHook();
        ws.getWhitelist().add(Pattern.compile("/room/(" + ROOM_RE + ")/ws"),
                              "\\1");
        ws.getWhitelist().add("/api/ws", "");
        try {
            runner.setup();
        } catch (PluginException exc) {
            System.err.println(exc);
            System.exit(2);
        } catch (Exception exc) {
            exc.printStackTrace();
            System.exit(2);
        }
        // Socket is only bound in run(), so we can create the server here.
        InstantWebSocketServer srv = runner.makeServer();
        if (startupCmd != null) runCommand(startupCmd);
        LOGGER.info("Listening on " + formatAddress(srv.getAddress()));
        srv.spawn();
    }

    private static PrintStream resolveLogFile(String path) {
        if (path == null || path.equals("-")) {
            return System.err;
        } else {
            try {
                return new PrintStream(new FileOutputStream(path, true),
                                       true);
            } catch (FileNotFoundException exc) {
                // Should not happen.
                throw new RuntimeException(exc);
            }
        }
    }

    private static int runCommand(String cmdline) {
        ProcessBuilder pb = new ProcessBuilder(cmdline.trim().split("\\s+"));
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        try {
            Process p = pb.start();
            return p.waitFor();
        } catch (IOException exc) {
            return Integer.MIN_VALUE;
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            return Integer.MIN_VALUE + 1;
        }
    }

    private String formatAddress(InetSocketAddress addr) {
        InetAddress baseAddr = addr.getAddress();
        if (baseAddr.isAnyLocalAddress())
            return "*:" + addr.getPort();
        String hostname = baseAddr.getHostName();
        String hostaddr = baseAddr.getHostAddress();
        if (hostname.equals(hostaddr))
            return hostname + ":" + addr.getPort();
        return hostname + "[" + hostaddr + "]:" + addr.getPort();
    }

}
