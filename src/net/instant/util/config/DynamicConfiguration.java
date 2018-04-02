package net.instant.util.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DynamicConfiguration implements Configuration {

    public static final Configuration PROPERTY_SOURCE = new Configuration() {
        public String get(String key) {
            return System.getProperty(key);
        }
    };

    public static final Configuration ENV_SOURCE = new Configuration() {
        public String get(String key) {
            return System.getenv(key.toUpperCase().replace(".", "_"));
        }
    };

    private final List<Configuration> sources;
    private final Map<String, String> data;

    public DynamicConfiguration() {
        sources = new ArrayList<Configuration>();
        data = new LinkedHashMap<String, String>();
    }

    public Map<String, String> getData() {
        return data;
    }

    public String get(String key) {
        if (data.containsKey(key)) return data.get(key);
        String ret = null;
        for (Configuration src : sources) {
            ret = src.get(key);
            if (ret != null) break;
        }
        data.put(key, ret);
        return ret;
    }

    public String getRaw(String key) {
        return data.get(key);
    }

    public void put(String key, String value) {
        data.put(key, value);
    }
    public void putAll(
            Iterable<? extends Map.Entry<String, String>> entries) {
        for (Map.Entry<String, String> e : entries) {
            put(e.getKey(), e.getValue());
        }
    }

    public void remove(String key) {
        data.remove(key);
    }

    public void addSource(Configuration source) {
        sources.add(source);
    }
    public void removeSource(Configuration source) {
        sources.remove(source);
    }

    public static DynamicConfiguration makeDefault() {
        DynamicConfiguration ret = new DynamicConfiguration();
        ret.addSource(PROPERTY_SOURCE);
        ret.addSource(ENV_SOURCE);
        return ret;
    }

}
