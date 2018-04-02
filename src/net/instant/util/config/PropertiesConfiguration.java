package net.instant.util.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesConfiguration implements Configuration {

    private final Properties base;

    public PropertiesConfiguration(Properties base) {
        this.base = base;
    }
    public PropertiesConfiguration(File path) {
        this(loadProperties(path));
    }

    public Properties getBase() {
        return base;
    }

    public String get(String key) {
        return base.getProperty(key);
    }

    public static final Properties loadProperties(File path) {
        Properties ret = new Properties();
        try {
            InputStream in = new FileInputStream(path);
            try {
                ret.load(in);
            } finally {
                in.close();
            }
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
        return ret;
    }

}
