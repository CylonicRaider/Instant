package net.instant.util;

public interface Configuration {

    Configuration NULL = new DynamicConfiguration();

    Configuration DEFAULT = DynamicConfiguration.makeDefault();

    String get(String key);

}
