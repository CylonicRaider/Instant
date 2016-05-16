package net.instant;

import net.instant.InstantWebSocketServer;
import net.instant.hooks.Error404Hook;
import net.instant.hooks.RoomWebSocketHook;
import net.instant.hooks.RedirectHook;
import net.instant.hooks.StaticFileHook;
import net.instant.proto.MessageDistributor;
import net.instant.util.FileCache;
import net.instant.util.Util;
import org.java_websocket.server.WebSocketServer;

public class Main implements Runnable {

    public static final String VERSION = "1.0";
    public static final String ROOM_RE =
        "[a-zA-Z](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?";

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                           "[%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL " +
                           "%4$-7s %3$-20s] %5$s %6$s%n");
    }

    private final String[] args;
    private InstantWebSocketServer srv;
    private MessageDistributor distr;

    public Main(String[] args) {
        this.args = args;
    }

    public int getArguments() {
        return args.length;
    }

    public String getArgument(int i) {
        if (i < 0 || i > args.length)
            throw new IndexOutOfBoundsException("Invalid index " + i);
        return args[i];
    }

    public InstantWebSocketServer getServer() {
        return srv;
    }
    public MessageDistributor getDistributor() {
        return distr;
    }

    protected void setServer(InstantWebSocketServer s) {
        srv = s;
    }
    protected void setDistributor(MessageDistributor d) {
        distr = d;
    }

    protected void parseArguments() {
        if (args.length != 1) {
            System.err.println("USAGE: ... <port>");
            System.exit(1);
        } else if (args[0].equals("--help")) {
            System.err.println("USAGE: ... <port>");
            System.exit(0);
        } else {
            int i;
            try {
                i = Integer.parseInt(args[0]);
            } catch (NumberFormatException exc) {
                System.err.println("USAGE: ... <port>");
                System.exit(1);
                return;
            }
            srv = new InstantWebSocketServer(i);
        }
    }

    public void run() {
        parseArguments();
        distr = new MessageDistributor();
        getServer().addHook(new RoomWebSocketHook(distr));
        getServer().addHook(new RedirectHook("/room/(" + ROOM_RE + ")",
            "/room/\\1/"));
        StaticFileHook files = new StaticFileHook(new FileCache());
        files.whitelistCWD("/static/.*");
        files.whitelistCWD("/pages/.*");
        files.whitelistResources("/static/.*");
        files.whitelistResources("/pages/.*");
        files.alias("/", "/pages/main.html");
        files.alias("/favicon.ico", "/static/logo-static_128x128.ico");
        files.alias("/([^/]+\\.html)", "/pages/\\1", true);
        files.alias("/room/" + ROOM_RE + "/", "/static/room.html");
        files.matchContentType(".*\\.html", "text/html; charset=utf-8");
        files.matchContentType(".*\\.css", "text/css; charset=utf-8");
        files.matchContentType(".*\\.js", "application/javascript; " +
                               "charset=utf-8");
        files.matchContentType(".*\\.svg", "image/svg+xml; charset=utf-8");
        getServer().addHook(files);
        getServer().addHook(new Error404Hook());
        getServer().run();
    }

}
