package net.instant.util.argparse;

import java.util.Map;

public interface ParseResult {

    Map<BaseOption<?>, ?> getData();

    boolean contains(BaseOption<?> key);

    <X> X get(BaseOption<X> key);

}
