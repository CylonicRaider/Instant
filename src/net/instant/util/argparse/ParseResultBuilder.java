package net.instant.util.argparse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParseResultBuilder implements ParseResult {

    private final Map<BaseOption<?>, OptionValue<?>> data;

    public ParseResultBuilder(Iterable<OptionValue<?>> values) {
        data = new LinkedHashMap<BaseOption<?>, OptionValue<?>>();
        if (values != null) for (OptionValue<?> v : values) put(v);
    }

    public Map<BaseOption<?>, OptionValue<?>> getData() {
        return Collections.unmodifiableMap(data);
    }

    public <X> OptionValue<X> getRaw(BaseOption<X> opt) {
        @SuppressWarnings("unchecked")
        OptionValue<X> ret = (OptionValue<X>) data.get(opt);
        return ret;
    }

    public <X> X get(BaseOption<X> opt) {
        OptionValue<X> v = getRaw(opt);
        return (v == null) ? null : v.getValue();
    }

    public <X> void put(OptionValue<X> v) {
        if (v == null) return;
        @SuppressWarnings("unchecked")
        OptionValue<X> o = (OptionValue<X>) data.get(v.getOption());
        data.put(v.getOption(), v.merge(o));
    }

}
