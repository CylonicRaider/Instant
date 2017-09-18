package net.instant;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.instant.hooks.APIWebSocketHook;
import net.instant.util.Formats;
import net.instant.util.Logging;
import net.instant.util.argparse.ArgumentParser;
import net.instant.util.argparse.BaseOption;
import net.instant.util.argparse.ParseException;
import net.instant.util.argparse.ParseResult;
import net.instant.util.argparse.ValueOption;
import net.instant.util.fileprod.FSResourceProducer;
import net.instant.ws.InstantWebSocketServer;

public class Main implements Runnable {

    public static final String APPNAME = "Instant";
    public static final String VERSION = "1.4.3";
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
            Formats.escapeJSString(VERSION, true),
            Formats.escapeJSString(FINE_VERSION, true));
    }

    private final String[] args;
    private InstantRunner runner;

    public Main(String[] args) {
        this.args = args;
        runner = new InstantRunner();
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
        p.addHelp();
        BaseOption<String> optHost = p.add(ValueOption.of(String.class,
            "host", 'h', "Address to bind to").defaultsTo("*"));
        BaseOption<Integer> optPort = p.add(ValueOption.of(Integer.class,
            "port", 'p', "Port to bind to").defaultsTo(8080));
        BaseOption<String> optWebroot = p.add(ValueOption.of(String.class,
            "webroot", 'r', "Path containing static directories")
            .defaultsTo("."));
        ParseResult r = parseArgumentsInner(p);
        String host = r.get(optHost);
        if (host.equals("*")) host = null;
        runner.setHost(host);
        runner.setPort(r.get(optPort));
        runner.setWebroot(new File(r.get(optWebroot)));
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
        parseArguments(new ArgumentParser(APPNAME));
        runner.addFileAlias("/", "/pages/main.html");
        runner.addFileAlias("/favicon.ico",
                            "/static/logo-static_128x128.ico");
        runner.addFileAlias(Pattern.compile("/([^/]+)\\.html"),
                            "/pages/\\1.html");
        runner.addFileAlias(Pattern.compile("/room/" + ROOM_RE + "/"),
                            "/static/room.html");
        runner.addRedirect(Pattern.compile("/room/" + ROOM_RE), "\\0/", 301);
        runner.addSyntheticFile("/static/version.js", VERSION_FILE);
        FSResourceProducer prod = runner.makeSourceFiles();
        prod.whitelist("/pages/.*");
        prod.whitelist("/static/.*");
        APIWebSocketHook ws = runner.makeAPIHook();
        ws.getWhitelist().add(Pattern.compile("/room/(" + ROOM_RE + ")/ws"),
                              "\\1");
        ws.getWhitelist().add("/api/ws", "");
        InstantWebSocketServer srv = runner.makeServer();
        srv.run();
    }

}
