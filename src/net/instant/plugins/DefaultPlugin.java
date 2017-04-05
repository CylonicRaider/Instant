package net.instant.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.instant.api.API1;
import net.instant.api.PluginData;
import net.instant.api.Utilities;
import net.instant.util.Util;

public class DefaultPlugin {

    public enum PluginResource {
        DEPENDENCY("deps"),
        STYLESHEET("styles"),
        SCRIPT("scripts"),
        LIBRARY("libs"),
        CODE("code"),
        MAIN("main");

        public final String name;

        private PluginResource(String name) {
            this.name = name;
        }

        public String get(PluginData data) {
            return data.getAttribute("Frontend-" + name);
        }
        public List<String> getList(PluginData data) {
            return splitAttribute(get(data));
        }

        public static List<String> splitAttribute(String val) {
            List<String> ret = new ArrayList<String>();
            if (val != null) {
                for (String item : val.split("\\s*,\\s*")) {
                    if (item.isEmpty()) continue;
                    ret.add(item.trim());
                }
            }
            return ret;
        }

    }

    public static Object initInstantPlugin1(API1 api, PluginData data) {
        if (Utilities.isTrue(data.getAttribute("Frontend-Plugin")))
            bootstrapFrontendPlugin(api, data);
        return null;
    }

    protected static void bootstrapFrontendPlugin(API1 api, PluginData data) {
        StringBuilder sb = new StringBuilder();
        List<String> items;
        for (PluginResource res : PluginResource.values()) {
            switch (res) {
                case MAIN:
                    String item = res.get(data);
                    if (Util.nonempty(item)) {
                        if (sb.length() != 0) sb.append(", ");
                        sb.append("main: function() { ").append(item);
                        sb.append(" }");
                    }
                    break;
                case SCRIPT:
                    items = res.getList(data);
                    if (! items.isEmpty()) {
                        StringBuilder ssb = new StringBuilder();
                        for (String i : items) {
                            String[] parts = i.split("#", 2);
                            String url = parts[0];
                            Map<String, String> params;
                            if (parts.length == 1) {
                                params = Collections.emptyMap();
                            } else {
                                params = Util.parseQueryString(parts[1]);
                            }
                            if (ssb.length() != 0) ssb.append(", ");
                            ssb.append("{url: ");
                            ssb.append(Util.escapeJSString(url, true));
                            if (Util.isTrue(params.get("before")))
                                ssb.append(", before: true");
                            if (Util.isTrue(params.get("after")))
                                ssb.append(", after: true");
                            if (Util.isTrue(params.get("isolate")))
                                ssb.append(", isolate: true");
                            ssb.append("}");
                        }
                        if (sb.length() != 0) sb.append(", ");
                        sb.append(res.name).append(": [");
                        sb.append(ssb).append(']');
                    }
                    break;
                default:
                    items = res.getList(data);
                    if (! items.isEmpty()) {
                        StringBuilder ssb = new StringBuilder();
                        for (String i : items) {
                            if (ssb.length() != 0) ssb.append(", ");
                            ssb.append(Utilities.escapeStringJS(i, true));
                        }
                        if (sb.length() != 0) sb.append(", ");
                        sb.append(res.name).append(": [");
                        sb.append(ssb).append(']');
                    }
            }
        }
        String escname = Utilities.escapeStringJS(data.getName(), true);
        api.addSiteCode("Instant.loadPlugin(" + escname + ", {" + sb + "});");
    }

}
