package net.instant;

import java.io.File;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.regex.Pattern;
import net.instant.api.API1;
import net.instant.api.Counter;
import net.instant.api.FileGenerator;
import net.instant.api.MessageHook;
import net.instant.api.PluginData;
import net.instant.api.RequestHook;
import net.instant.api.RoomGroup;
import net.instant.hooks.APIWebSocketHook;
import net.instant.hooks.CodeHook;
import net.instant.hooks.RedirectHook;
import net.instant.hooks.StaticFileHook;
import net.instant.plugins.DefaultPlugin;
import net.instant.plugins.PluginException;
import net.instant.plugins.PluginManager;
import net.instant.proto.APIHook;
import net.instant.proto.MessageDistributor;
import net.instant.util.DefaultStringMatcher;
import net.instant.util.UniqueCounter;
import net.instant.util.Util;
import net.instant.util.fileprod.APIProducer;
import net.instant.util.fileprod.FSResourceProducer;
import net.instant.util.fileprod.ListProducer;
import net.instant.util.fileprod.StringProducer;
import net.instant.ws.InstantWebSocketServer;

public class InstantRunner implements API1 {

    private String host;
    private int port;
    private File webroot;
    private PrintStream httpLog;
    private InstantWebSocketServer server;
    private RedirectHook redirects;
    private StaticFileHook files;
    private APIWebSocketHook wsAPI;
    private ListProducer pluginFiles;
    private StringProducer stringFiles;
    private FSResourceProducer sourceFiles;
    private MessageDistributor distributor;
    private PluginManager plugins;

    public InstantRunner() {
        host = null;
        port = 8080;
        webroot = null;
    }

    public String getHost() {
        return host;
    }
    public void setHost(String h) {
        host = h;
    }

    public int getPort() {
        return port;
    }
    public void setPort(int p) {
        port = p;
    }

    public File getWebroot() {
        return webroot;
    }
    public void setWebroot(File p) {
        webroot = p;
    }

    public PrintStream getHTTPLog() {
        return httpLog;
    }
    public void setHTTPLog(PrintStream s) {
        httpLog = s;
    }

    public InstantWebSocketServer getServer() {
        return server;
    }
    public void setServer(InstantWebSocketServer srv) {
        server = srv;
    }
    public InstantWebSocketServer makeServer() {
        if (server == null) {
            InetSocketAddress addr;
            if (host == null) {
                addr = new InetSocketAddress(port);
            } else {
                addr = new InetSocketAddress(host, port);
            }
            server = new InstantWebSocketServer(addr);
            server.setHTTPLog(httpLog);
            server.addInternalHook(makeRedirectHook());
            server.addInternalHook(makeFileHook());
            server.addInternalHook(makeAPIHook());
            server.addInternalHook(CodeHook.NOT_FOUND);
            server.addInternalHook(CodeHook.METHOD_NOT_ALLOWED);
        }
        return server;
    }

    public RedirectHook getRedirectHook() {
        return redirects;
    }
    public void setRedirectHook(RedirectHook hook) {
        redirects = hook;
    }
    public RedirectHook makeRedirectHook() {
        if (redirects == null) {
            redirects = new RedirectHook();
        }
        return redirects;
    }

    public StaticFileHook getFileHook() {
        return files;
    }
    public void setFileHook(StaticFileHook hook) {
        files = hook;
    }
    public StaticFileHook makeFileHook() {
        if (files == null) {
            files = new StaticFileHook();
            ListProducer l = files.getProducer().getProducer();
            l.add(makePluginFiles());
            l.add(makeStringFiles());
            l.add(makeSourceFiles());
        }
        return files;
    }

    public APIWebSocketHook getAPIHook() {
        return wsAPI;
    }
    public void setAPIHook(APIWebSocketHook hook) {
        wsAPI = hook;
    }
    public APIWebSocketHook makeAPIHook() {
        if (wsAPI == null) {
            wsAPI = new APIWebSocketHook(makeDistributor());
            wsAPI.addInternalHook(new APIHook());
        }
        return wsAPI;
    }

    public ListProducer getPluginFiles() {
        return pluginFiles;
    }
    public void setPluginFiles(ListProducer prod) {
        pluginFiles = prod;
    }
    public ListProducer makePluginFiles() {
        if (pluginFiles == null) {
            pluginFiles = new ListProducer();
        }
        return pluginFiles;
    }

    public StringProducer getStringFiles() {
        return stringFiles;
    }
    public void setStringFiles(StringProducer prod) {
        stringFiles = prod;
    }
    public StringProducer makeStringFiles() {
        if (stringFiles == null) {
            stringFiles = new StringProducer();
            // Extended by plugins.
            stringFiles.addFile("/static/site.js", "\n");
            // Added my Main.
            stringFiles.addFile("/static/version.js", "");
        }
        return stringFiles;
    }

    public FSResourceProducer getSourceFiles() {
        return sourceFiles;
    }
    public void setSourceFiles(FSResourceProducer prod) {
        sourceFiles = prod;
    }
    public FSResourceProducer makeSourceFiles() {
        if (sourceFiles == null) {
            sourceFiles = new FSResourceProducer(webroot,
                makePlugins().getClassLoader());
        }
        return sourceFiles;
    }

    public MessageDistributor getDistributor() {
        return distributor;
    }
    public void setDistributor(MessageDistributor distr) {
        distributor = distr;
    }
    public MessageDistributor makeDistributor() {
        if (distributor == null) {
            distributor = new MessageDistributor();
        }
        return distributor;
    }

    public PluginManager getPlugins() {
        return plugins;
    }
    public void setPlugins(PluginManager mgr) {
        plugins = mgr;
    }
    public PluginManager makePlugins() {
        if (plugins == null) {
            plugins = new PluginManager(this);
        }
        return plugins;
    }

    public void addRequestHook(RequestHook hook) {
        makeServer().addHook(hook);
    }

    public void addFileGenerator(FileGenerator gen) {
        makePluginFiles().add(new APIProducer(gen));
    }

    public void addFileAlias(String from, String to) {
        makeFileHook().getAliases().add(from, to);
    }

    public void addFileAlias(Pattern from, String to) {
        makeFileHook().getAliases().add(from, to);
    }

    public void addContentType(String pattern, String type) {
        makeFileHook().getContentTypes().add(new DefaultStringMatcher(
            Pattern.compile(pattern), type, false));
    }

    public void addRedirect(String from, String to, int code) {
        makeRedirectHook().add(from, to, code);
    }

    public void addRedirect(Pattern from, String to, int code) {
        makeRedirectHook().add(from, to, code);
    }

    public void addMessageHook(MessageHook hook) {
        makeAPIHook().addHook(hook);
    }

    public void addSyntheticFile(String name, String content) {
        makeStringFiles().addFile(name, content);
    }

    public void addSiteCode(String code) {
        makeStringFiles().appendFile("/static/site.js", code + "\n");
    }

    public Counter getCounter() {
        return UniqueCounter.INSTANCE;
    }

    public RoomGroup getRooms() {
        return makeDistributor();
    }

    public Object handleDefault(PluginData data) {
        return DefaultPlugin.initInstantPlugin1(this, data);
    }

    public String getConfiguration(String name) {
        return Util.getConfiguration(name, true);
    }

    public Object getPluginData(String name) throws IllegalArgumentException,
            IllegalStateException {
        return makePlugins().getData(name);
    }

    public void addPluginPath(File path) {
        makePlugins().getFetcher().addPath(path);
    }

    public void addPlugin(String name) {
        makePlugins().queueFetch(name);
    }

    public void setupPlugins() throws PluginException {
        makePlugins().setup();
    }

}
