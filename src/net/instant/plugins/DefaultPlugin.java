package net.instant.plugins;

import java.util.Map;
import java.util.Set;
import net.instant.api.API1;
import net.instant.api.PluginData;
import net.instant.util.Formats;
import net.instant.util.Util;

public class DefaultPlugin {

    public static final PluginAttribute<Boolean> FRONTEND_PLUGIN =
        new BooleanAttribute("Frontend-Plugin");

    public enum PluginResource {

        STYLES("styles") {

            public final PluginAttribute<Set<String>> ATTR =
                new StringSetAttribute("Frontend-Styles");


            public String parse(PluginData data) {
                return parseSet(data, ATTR);
            }

        },
        SCRIPTS("scripts") {

            public final PluginAttribute<Set<String>> ATTR =
                new StringSetAttribute("Frontend-Scripts");

            public String parse(PluginData data) {
                Set<String> urls = ATTR.get(data);
                if (urls.isEmpty()) return null;
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (String url : urls) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(", ");
                    }
                    String[] parts = url.split("#", 2);
                    sb.append("{url: ");
                    sb.append(Util.escapeStringJS(parts[0], true));
                    Map<String, String> params =
                        Formats.parseQueryString((parts.length == 1) ? "" :
                                                 parts[1]);
                    for (String prop : new String[] { "before", "after",
                                                      "isolate" }) {
                        if (! Util.isTrue(params.get(prop))) continue;
                        sb.append(", ");
                        sb.append(prop);
                        sb.append(": true");
                    }
                    sb.append("}");
                }
                return sb.append("]").toString();
            }

        },
        LIBS("libs") {

            public final PluginAttribute<Set<String>> ATTR =
                new StringSetAttribute("Frontend-Libs");

            public String parse(PluginData data) {
                return parseSet(data, ATTR);
            }

        },
        CODE("code") {

            public final PluginAttribute<Set<String>> ATTR =
                new StringSetAttribute("Frontend-Code");

            public String parse(PluginData data) {
                return parseSet(data, ATTR);
            }

        },
        MAIN("main") {

            public final PluginAttribute<String> ATTR =
                new StringAttribute("Frontend-Main");

            public String parse(PluginData data) {
                String source = ATTR.get(data);
                if (source == null) return null;
                return "function() { " + source + "; }";
            }

        };

        private final String name;

        private PluginResource(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public abstract String parse(PluginData data);

        // HACK: Default implementation of parse().
        private static String parseSet(PluginData data,
                                       PluginAttribute<Set<String>> attr) {
            Set<String> values = attr.get(data);
            if (values.isEmpty()) return null;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (String item : attr.get(data)) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(Util.escapeStringJS(item, true));
            }
            return sb.append("]").toString();
        }

    }

    public static final PluginAttribute<Set<String>> FRONTEND_DEPS =
        new StringSetAttribute("Frontend-Deps");

    public static Object initInstantPlugin1(API1 api, PluginData data) {
        if (FRONTEND_PLUGIN.get(data)) initFrontendPlugin(api, data);
        return null;
    }

    public static void initFrontendPlugin(API1 api, PluginData data) {
        StringBuilder res = new StringBuilder("Instant.loadPlugin(");
        res.append(Util.escapeStringJS(data.getName(), true));
        res.append(", {");
        boolean first = true;
        for (PluginResource r : PluginResource.values()) {
            String s = r.parse(data);
            if (s == null) continue;
            if (first) {
                first = false;
            } else {
                res.append(", ");
            }
            res.append(Util.escapeStringJS(r.getName(), true));
            res.append(": ");
            res.append(s);
        }
        api.addSiteCode(res.append("});").toString());
    }

}
