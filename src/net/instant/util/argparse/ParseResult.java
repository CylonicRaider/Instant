package net.instant.util.argparse;

import java.util.Map;

public interface ParseResult {

    public Map<BaseOption<?>, OptionValue<?>> getData();

    public <X> OptionValue<X> getRaw(BaseOption<X> opt);

    public <X> X get(BaseOption<X> opt);

}
