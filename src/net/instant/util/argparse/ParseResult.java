package net.instant.util.argparse;

import java.util.Map;

public interface ParseResult {

    Map<BaseOption<?>, OptionValue<?>> getData();

    <X> boolean contains(BaseOption<X> key);

    <X> OptionValue<X> getRaw(BaseOption<X> key);

    <X> X get(BaseOption<X> key);

}
