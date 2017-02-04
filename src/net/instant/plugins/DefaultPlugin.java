package net.instant.plugins;

import java.util.ArrayList;
import java.util.List;
import net.instant.api.API1;
import net.instant.api.PluginData;
import net.instant.api.Utilities;

public class DefaultPlugin {

    public enum PluginResource {
        DEPENDENCY("deps"),
        STYLESHEET("styles"),
        SCRIPT("scripts"),
        LIBRARY("libs"),
        SYNC_LIBRARY("synclibs");

        public final String name;

        private PluginResource(String name) {
            this.name = name;
        }

        public List<String> get(PluginData data) {
            return splitAttribute(data.getAttribute("Frontend-" + name));
        }

        public static List<String> splitAttribute(String val) {
            List<String> ret = new ArrayList<String>();
            for (String item : val.split("\\s*,\\s*")) {
                if (item.isEmpty()) continue;
                ret.add(item.trim());
            }
            return ret;
        }

    }

    public static void initInstantPlugin1(API1 api, PluginData data) {
        if (Utilities.nonempty(data.getAttribute("Frontend-Plugin")))
            bootstrapFrontendPlugin(api, data);
    }

    protected static void bootstrapFrontendPlugin(API1 api, PluginData data) {
        StringBuilder sb = new StringBuilder();
        for (PluginResource res : PluginResource.values()) {
            List<String> items = res.get(data);
            if (! items.isEmpty()) {
                StringBuilder ssb = new StringBuilder();
                for (String i : items) {
                    if (ssb.length() != 0) ssb.append(", ");
                    ssb.append(Utilities.escapeStringJS(i, true));
                }
                if (sb.length() != 0) sb.append(", ");
                sb.append(res.name).append(": [").append(ssb).append(']');
            }
        }
        String escname = Utilities.escapeStringJS(data.getName(), true);
        api.addSiteCode("Instant.plugins.load(" + escname + ", {" + sb + "});");
    }

}
