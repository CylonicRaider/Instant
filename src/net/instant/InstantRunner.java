package net.instant;

import java.util.regex.Pattern;
import net.instant.api.API1;
import net.instant.api.Counter;
import net.instant.api.FileGenerator;
import net.instant.api.MessageHook;
import net.instant.api.PluginData;
import net.instant.api.RequestHook;
import net.instant.api.RoomGroup;
import net.instant.hooks.APIWebSocketHook;
import net.instant.hooks.RedirectHook;
import net.instant.hooks.StaticFileHook;
import net.instant.plugins.DefaultPlugin;
import net.instant.plugins.PluginManager;
import net.instant.proto.MessageDistributor;
import net.instant.util.DefaultStringMatcher;
import net.instant.util.UniqueCounter;
import net.instant.util.Util;
import net.instant.util.fileprod.APIProducer;
import net.instant.util.fileprod.StringProducer;
import net.instant.ws.InstantWebSocketServer;

public class InstantRunner implements API1 {

    private InstantWebSocketServer server;
    private RedirectHook redirects;
    private StaticFileHook files;
    private APIWebSocketHook wsAPI;
    private StringProducer strings;
    private MessageDistributor distributor;
    private PluginManager plugins;

    public void addRequestHook(RequestHook hook) {
        server.addHook(hook);
    }

    public void addFileGenerator(FileGenerator gen) {
        files.getProducer().getProducer().add(new APIProducer(gen));
    }

    public void addFileAlias(String from, String to) {
        files.getAliases().add(from, to);
    }

    public void addFileAlias(Pattern from, String to) {
        files.getAliases().add(from, to);
    }

    public void addContentType(String pattern, String type) {
        files.getContentTypes().add(new DefaultStringMatcher(
            Pattern.compile(pattern), type, false));
    }

    public void addRedirect(String from, String to, int code) {
        redirects.add(from, to, code);
    }

    public void addRedirect(Pattern from, String to, int code) {
        redirects.add(from, to, code);
    }

    public void addMessageHook(MessageHook hook) {
        wsAPI.addHook(hook);
    }

    public void addSyntheticFile(String name, String content) {
        strings.addFile(name, content);
    }

    public void addSiteCode(String code) {
        strings.appendFile("/static/site.js", code + "\n");
    }

    public Counter getCounter() {
        return UniqueCounter.INSTANCE;
    }

    public RoomGroup getRooms() {
        return distributor;
    }

    public Object handleDefault(PluginData data) {
        return DefaultPlugin.initInstantPlugin1(this, data);
    }

    public String getConfiguration(String name) {
        return Util.getConfiguration(name, true);
    }

    public Object getPluginData(String name) throws IllegalArgumentException,
            IllegalStateException {
        return plugins.getData(name);
    }

}
