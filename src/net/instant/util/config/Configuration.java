package net.instant.util.config;

public interface Configuration {

    Configuration NULL = new DynamicConfiguration();

    Configuration DEFAULT = DynamicConfiguration.makeDefault();

    String get(String key);

}
