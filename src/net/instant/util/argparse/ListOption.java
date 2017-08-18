package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ListOption<E> extends Option<List<E>> {

    public ListOption(String name, boolean positional) {
        // Now *that* syntax is lovely.
        super(name, 1, positional, false, Collections.<E>emptyList());
    }

    public String getPlaceholder(int index) {
        String it = getItemPlaceholder(), sep = getSeparator();
        return it + sep + it + sep + "...";
    }

    public OptionValue<List<E>> parse(OptionValue<List<E>> old,
                                      String[] args) {
        List<E> newVal = new ArrayList<E>();
        if (old != null) newVal.addAll(old.getValue());
        String sep = getSeparator();
        for (String s : args) {
            newVal.addAll(parseItems(s.split(sep)));
        }
        OptionValue<List<E>> ret = wrap(Collections.unmodifiableList(newVal));
        return ret;
    }

    public abstract String getSeparator();

    public abstract String getItemPlaceholder();

    public abstract List<E> parseItems(String[] raw);

}
