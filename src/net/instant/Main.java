package net.instant;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.instant.hooks.Error404Hook;
import net.instant.hooks.RoomWebSocketHook;
import net.instant.hooks.RedirectHook;
import net.instant.hooks.StaticFileHook;
import net.instant.plugins.PluginException;
import net.instant.proto.MessageDistributor;
import net.instant.util.Logging;
import net.instant.util.StringSigner;
import net.instant.util.Util;
import net.instant.util.argparse.ActionOption;
import net.instant.util.argparse.ArgParser;
import net.instant.util.argparse.IntegerOption;
import net.instant.util.argparse.ParseException;
import net.instant.util.argparse.ParseResult;
import net.instant.util.argparse.PathListOption;
import net.instant.util.argparse.StringListOption;
import net.instant.util.argparse.StringOption;
import net.instant.util.fileprod.FileProducer;
import net.instant.util.fileprod.FilesystemProducer;
import net.instant.util.fileprod.ResourceProducer;
import net.instant.util.fileprod.StringProducer;
import net.instant.ws.CookieHandler;
import net.instant.ws.InstantWebSocketServer;
import org.java_websocket.server.WebSocketServer;

public class Main implements Runnable {

    public static final String VERSION = "1.4.2";
    public static final String ROOM_RE =
        "[a-zA-Z](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?";
    public static final String STAGING_RE = "dev/[a-zA-Z0-9-]+";
    public static final String FINE_VERSION;

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
            Util.escapeJSString(VERSION, true),
            Util.escapeJSString(FINE_VERSION, true));
    }

    private final String[] args;
    private InstantRunner runner;
    private String startupCmd;

    public Main(String[] args) {
        this.args = args;
        this.runner = new InstantRunner();
        this.startupCmd = null;
    }

    public int getArguments() {
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

    protected void parseArguments() {
        ArgParser p = new ArgParser("Instant");
        p.addHelp();
        p.add(new ActionOption("version") {
            public void run() {
                String output = "Instant v" + VERSION;
                if (FINE_VERSION != null)
                    output += " (" + FINE_VERSION + ")";
                System.err.println(output);
                System.exit(0);
            }
        }, "Show the current version");
        StringOption host = p.add(new StringOption("host", false, "*"),
            "Host to bind to");
        IntegerOption port = p.add(new IntegerOption("port", true, 8080),
            "Port number to use");
        StringOption httplog = p.add(new StringOption("http-log", false,
            "-"), "Log file for HTTP requests");
        StringOption dbglog = p.add(new StringOption("debug-log", false,
            "-"), "Log file for debugging");
        StringOption loglevel = p.add(new StringOption("log-level", false,
           "INFO"), "Logging level");
        PathListOption pluginPath = p.add(new PathListOption(
            "plugin-path", false), "Path to search for plugins in");
        StringListOption plugins = p.add(new StringListOption(
            "plugins", false), "List of plugins to load");
        StringOption cmdOpt = p.add(new StringOption("startup-cmd", false,
            null), "A shell command to run just before entering the " +
            "main loop");
        ParseResult r;
        try {
            r = p.parse(args);
        } catch (ParseException exc) {
            System.err.println(exc.getMessage());
            System.exit(1);
            return;
        }
        String hostName = host.get(r);
        if (hostName.equals("*")) hostName = null;
        runner.setHost(hostName);
        runner.setPort(port.get(r));
        runner.setHTTPLog(resolveOutputStream(httplog.get(r)));
        Logging.redirectToStream(resolveOutputStream(dbglog.get(r)));
        Logging.setLevel(Level.parse(loglevel.get(r)));
        for (File path : pluginPath.get(r))
            runner.addPluginPath(path);
        for (String plugin : plugins.get(r))
            runner.addPlugin(plugin);
        startupCmd = cmdOpt.get(r);
    }

    public void run() {
        parseArguments();
        LOGGER.info("Preparing");
        try {
            runner.makePluginManager().load();
        } catch (IOException exc) {
            exc.printStackTrace();
            System.exit(1);
            return;
        } catch (PluginException exc) {
            exc.printStackTrace();
            System.exit(1);
            return;
        }
        String signaturePath = Util.getConfiguration(
            "instant.cookies.keyfile");
        StringSigner signer = null;
        if (signaturePath != null)
            signer = StringSigner.getInstance(new File(signaturePath));
        if (signer == null)
            signer = StringSigner.getInstance(Util.getRandomness(64));
        runner.addFileAlias("/", "/pages/main.html");
        runner.addFileAlias("/favicon.ico",
                            "/static/logo-static_128x128.ico");
        runner.addFileAlias(Pattern.compile("/([^/]+\\.html)"),
                            "/pages/\\1");
        runner.addFileAlias(Pattern.compile("/room/" + ROOM_RE + "/"),
                            "/static/room.html");
        runner.addFileAlias(Pattern.compile("/(" + STAGING_RE + ")/"),
                            "/static/\\1/main.html");
        runner.addFileAlias(Pattern.compile("/(" + STAGING_RE + ")/room/" +
                            ROOM_RE + "/"), "/static/\\1/room.html");
        runner.addContentType(".*\\.html", "text/html; charset=utf-8");
        runner.addContentType(".*\\.css", "text/css; charset=utf-8");
        runner.addContentType(".*\\.js", "application/javascript; " +
                               "charset=utf-8");
        runner.addContentType(".*\\.svg", "image/svg+xml; charset=utf-8");
        runner.addContentType(".*\\.png", "image/png");
        runner.addContentType(".*\\.ico", "image/vnd.microsoft.icon");
        runner.addRedirect(Pattern.compile("/room/(" + ROOM_RE + ")"),
                           "/room/\\1/", 301);
        runner.addRedirect(Pattern.compile("/(" + STAGING_RE + ")"), "/\\1/",
                           301);
        runner.addRedirect(Pattern.compile("/(" + STAGING_RE + ")/room/(" +
                           ROOM_RE + ")"), "/\\1/room/\\2/", 301);
        InstantWebSocketServer srv = runner.make();
        srv.setCookieHandler(new CookieHandler(signer));
        FileProducer prod = runner.getFileHook().getProducer();
        FilesystemProducer fp = new FilesystemProducer(".", "");
        ResourceProducer rp = new ResourceProducer(
            runner.getPluginManager().getClassLoader());
        fp.whitelist("/static/.*");
        fp.whitelist("/pages/.*");
        rp.whitelist("/static/.*");
        rp.whitelist("/pages/.*");
        prod.addProducer(fp);
        prod.addProducer(rp);
        runner.getStringProducer().addFile("/static/version.js",
                                           VERSION_FILE);
        srv.addInternalHook(new Error404Hook());
        if (startupCmd != null) runCommand(startupCmd);
        LOGGER.info("Running");
        srv.run();
    }

    private static PrintStream resolveOutputStream(String path) {
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
        ProcessBuilder pb = new ProcessBuilder(cmdline.trim().split("\\s"));
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
