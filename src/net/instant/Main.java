package net.instant;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import net.instant.hooks.CodeHook;
import net.instant.util.Formats;
import net.instant.util.Logging;
import net.instant.util.argparse.ArgumentParser;
import net.instant.util.argparse.BaseOption;
import net.instant.util.argparse.ParseException;
import net.instant.util.argparse.ParseResult;
import net.instant.util.argparse.ValueOption;
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
    private String host;
    private int port;

    public Main(String[] args) {
        this.args = args;
    }

    public int getArgumentCount() {
        return args.length;
    }

    public String getArgument(int i) {
        if (i < 0 || i > args.length)
            throw new IndexOutOfBoundsException("Invalid index " + i);
        return args[i];
    }

    protected ParseResult parseArguments(ArgumentParser p) {
        p.addHelp();
        BaseOption<String> optHost = p.add(ValueOption.of(String.class,
            "host", 'h', "Address to bind to").defaultsTo("*"));
        BaseOption<Integer> optPort = p.add(ValueOption.of(Integer.class,
            "port", 'p', "Port to bind to").defaultsTo(8080));
        ParseResult r = parseArgumentsInner(p);
        host = r.get(optHost);
        port = r.get(optPort);
        if (host.equals("*")) host = "";
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
        InstantWebSocketServer srv = new InstantWebSocketServer(
            new InetSocketAddress(host, port));
        srv.addHook(CodeHook.NOT_FOUND);
        srv.run();
    }

}
