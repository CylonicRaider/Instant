package net.instant.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Configuration {

    public interface DataSource {

        String get(String key);

    }

    public static final DataSource PROPERTY_SOURCE = new DataSource() {
        public String get(String key) {
            return System.getProperty(key);
        }
    };

    public static final DataSource ENV_SOURCE = new DataSource() {
        public String get(String key) {
            return System.getenv(key.toUpperCase().replace(".", "_"));
        }
    };

    private final List<DataSource> sources;
    private final Map<String, String> data;

    public Configuration() {
        sources = new ArrayList<DataSource>();
        data = new HashMap<String, String>();
    }

    public String get(String key) {
        if (data.containsKey(key)) return data.get(key);
        String ret = null;
        for (DataSource src : sources) {
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

    public void remove(String key) {
        data.remove(key);
    }

    public void addSource(DataSource source) {
        sources.add(source);
    }
    public void removeSource(DataSource source) {
        sources.remove(source);
    }

    public static Configuration makeDefault() {
        Configuration ret = new Configuration();
        ret.addSource(PROPERTY_SOURCE);
        ret.addSource(ENV_SOURCE);
        return ret;
    }

}
