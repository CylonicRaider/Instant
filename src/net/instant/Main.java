package net.instant;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.instant.console.BackendConsoleManager;
import net.instant.hooks.APIWebSocketHook;
import net.instant.plugins.PluginException;
import net.instant.util.Logging;
import net.instant.util.argparse.Argument;
import net.instant.util.argparse.ArgumentParser;
import net.instant.util.argparse.KeyValue;
import net.instant.util.argparse.Option;
import net.instant.util.argparse.ParseResult;
import net.instant.util.fileprod.FSResourceProducer;

public class Main implements Runnable {

    public static final String APPNAME = "Instant";
    public static final String VERSION = "1.5.6";
    public static final String FINE_VERSION;
    public static final String DESCRIPTION = "A Web-based threaded chat " +
        "(server).";

    public static final String ROOM_RE =
        "[a-zA-Z](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?";
    public static final String STAGING_RE = "dev/[a-zA-Z0-9-]+";

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
        Option<String> host = p.add(Option.of(String.class, "host", 'h',
            "Host to bind to.").defaultsTo("*").withPlaceholder("<ADDR>")
            .withComment("\"*\" = all interfaces"));
        Argument<Integer> port = p.add(Argument.of(Integer.class, "port",
            "Port to bind to.").defaultsTo(8080));
        Option<File> webroot = p.add(Option.of(File.class, "webroot", 'r',
            "Path containing static directories.").defaultsTo(new File(".")));
        Option<List<KeyValue>> tlsFlags = p.add(Option.ofList(KeyValue.class,
            "tls", 't', "TLS configuration."));
        Option<File> httpLog = p.add(Option.of(File.class, "http-log", null,
            "Log file for HTTP requests.").defaultsTo(new File("-"))
            .withComment("\"-\" = standard error"));
        Option<File> debugLog = p.add(Option.of(File.class, "debug-log", null,
            "Log file for debugging.").defaultsTo(new File("-"))
            .withComment("\"-\" = standard error"));
        Option<String> logLevel = p.add(Option.of(String.class, "log-level",
            'L', "Logging level.").defaultsTo("INFO"));
        Option<List<File>> plugPath = p.add(Option.ofList(File.class,
            "plugin-path", 'P', "Path to search for plugins in.")
            .defaultsTo(new ArrayList<File>()));
        Option<List<String>> plugList = p.add(Option.ofList(String.class,
            "plugins", 'p', "List of plugins to load.")
            .defaultsTo(new ArrayList<String>()));
        Option<String> cmd = p.add(Option.of(String.class, "startup-cmd",
            null, "OS command to run before entering main loop."));
        Option<List<KeyValue>> options = p.add(Option.ofAccum(KeyValue.class,
            "option", 'o', "Additional configuration parameter."));
        Option<File> config = p.add(Option.of(File.class, "config", 'C',
            "Configuration file."));
        ParseResult r = parseArgumentsInner(p);
        String hostval = r.get(host);
        if (hostval.equals("*")) hostval = null;
        runner.setHost(hostval);
        runner.setPort(r.get(port));
        runner.setWebroot(r.get(webroot));
        runner.setSSLConfig(pairsToMap(r.get(tlsFlags)));
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
        return p.parseOrExit(args);
    }

    public void run() {
        Logging.captureExceptions(LOGGER);
        String version = VERSION;
        if (FINE_VERSION != null) version += " (" + FINE_VERSION + ")";
        parseArguments(new ArgumentParser(APPNAME, version, DESCRIPTION));
        LOGGER.info(APPNAME + " " + version);
        runner.addFileAlias("/",
                            "/pages/main.html");
        runner.addFileAlias("/favicon.ico",
                            "/static/logo-static_128x128.ico");
        runner.addFileAlias(Pattern.compile("/([^/]+\\.html)"),
                            "/pages/\\1");
        runner.addFileAlias(Pattern.compile("/room/" + ROOM_RE + "/"),
                            "/static/room.html");
        runner.addFileAlias(Pattern.compile("/(" + STAGING_RE + ")/"),
                            "/static/\\1/main.html");
        runner.addFileAlias(Pattern.compile("/(" + STAGING_RE + ")/room/" +
                                            ROOM_RE + "/"),
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
        runner.addRedirect(Pattern.compile("/(room/" + ROOM_RE + ")"),
                           "/\\1/", 301);
        runner.addRedirect(Pattern.compile("/(" + STAGING_RE + ")"),
                           "/\\1/", 301);
        runner.addRedirect(Pattern.compile("/(" + STAGING_RE + "/room/" +
                                           ROOM_RE + ")"),
                           "\\1/", 301);
        FSResourceProducer prod = runner.makeSourceFiles();
        prod.whitelist("/pages/.*");
        prod.whitelist("/static/.*");
        APIWebSocketHook ws = runner.makeAPIHook();
        ws.getWhitelist().add(Pattern.compile("/room/(" + ROOM_RE + ")/ws"),
                              "\\1");
        ws.getWhitelist().add("/api/ws", "");
        if (runner.getConsole() == null)
            runner.setConsole(BackendConsoleManager.makeDefault(this));
        try {
            runner.setup();
        } catch (PluginException exc) {
            System.err.println(exc);
            System.exit(2);
        } catch (Exception exc) {
            LOGGER.log(Level.SEVERE, "Exception during setup:", exc);
            System.exit(2);
        }
        runner.registerShutdownHook();
        if (startupCmd != null) runCommand(startupCmd);
        runner.launch();
    }

    private static <K, V> Map<K, V> pairsToMap(
            Iterable<? extends Map.Entry<K, V>> pairs) {
        if (pairs == null) return null;
        Map<K, V> ret = new LinkedHashMap<K, V>();
        for (Map.Entry<K, V> ent : pairs) {
            ret.put(ent.getKey(), ent.getValue());
        }
        return ret;
    }

    private static PrintStream resolveLogFile(File path) {
        if (path == null || path.getPath().equals("-")) {
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

}
