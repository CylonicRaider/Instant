package net.instant.util.argparse;

import java.util.Map;

public interface ParseResult {

    Map<ValueProcessor<?>, Object> getData();

    boolean contains(ValueProcessor<?> key);

    <T> T get(ValueProcessor<T> key);

}
