package net.instant.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.instant.api.API1;
import net.instant.api.PluginData;
import net.instant.util.Formats;
import net.instant.util.Util;

public class DefaultPlugin {

    public static final PluginAttribute<Boolean> FRONTEND_PLUGIN =
        new BooleanAttribute("Frontend-Plugin");

    public static abstract class Resource<T> {

        private final String name;
        private final PluginAttribute<T> attr;

        public Resource(String name, PluginAttribute<T> attr) {
            this.name = name;
            this.attr = attr;
        }

        public String getName() {
            return name;
        }

        public PluginAttribute<T> getAttribute() {
            return attr;
        }

        // Returns JS representation.
        public abstract String parse(PluginData data);

    }

    public static class StringSetResource extends Resource<Set<String>> {

        public StringSetResource(String name,
                                 PluginAttribute<Set<String>> attr) {
            super(name, attr);
        }
        public StringSetResource(String name) {
            super(name, new StringSetAttribute(name));
        }

        public String parse(PluginData data) {
            Set<String> values = getAttribute().get(data);
            if (values.isEmpty()) return null;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (String item : values) {
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

    public static final Resource<Set<String>> RES_DEPS =
        new StringSetResource("Deps");
    public static final Resource<Set<String>> RES_STYLES =
        new StringSetResource("Styles");
    public static final Resource<Set<String>> RES_SCRIPTS =
            new StringSetResource("Scripts") {

        public String parse(PluginData data) {
            Set<String> urls = getAttribute().get(data);
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

    };
    public static final Resource<Set<String>> RES_LIBS =
        new StringSetResource("Libs");
    public static final Resource<Set<String>> RES_CODE =
        new StringSetResource("Code");
    public static final Resource<String> RES_MAIN = new Resource<String>(
            "Main", new StringAttribute("Plugin-Main")) {

        public String parse(PluginData data) {
            String source = getAttribute().get(data);
            if (source == null) return null;
            return "function() { " + source + "; }";
        }

    };

    public static final List<Resource<?>> RESOURCES;

    static {
        RESOURCES = new ArrayList<Resource<?>>();
        RESOURCES.add(RES_DEPS);
        RESOURCES.add(RES_STYLES);
        RESOURCES.add(RES_SCRIPTS);
        RESOURCES.add(RES_LIBS);
        RESOURCES.add(RES_CODE);
        RESOURCES.add(RES_MAIN);
    }

    public static Object initInstantPlugin1(API1 api, PluginData data) {
        if (isFrontendPlugin(data)) initFrontendPlugin(api, data);
        return null;
    }

    public static boolean isFrontendPlugin(PluginData data) {
        return FRONTEND_PLUGIN.get(data);
    }

    public static void initFrontendPlugin(API1 api, PluginData data) {
        StringBuilder res = new StringBuilder("Instant.loadPlugin(");
        res.append(Util.escapeStringJS(data.getName(), true));
        res.append(", {");
        boolean first = true;
        for (Resource<?> r : RESOURCES) {
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
