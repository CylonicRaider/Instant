package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.List;

public abstract class ListOption<E> extends ValueOption<List<E>> {

    /* Caution, regular expression pattern. */
    private String separator = ",";

    public ListOption(String name, Character shortname, String help) {
        super(name, shortname, help);
    }

    public String getSeparator() {
        return separator;
    }
    public void setSeparator(String s) {
        separator = s;
    }
    public ListOption<E> withSeparator(String s) {
        separator = s;
        return this;
    }

    protected String getDefaultPlaceholder() {
        return getItemPlaceholder() + "[" + separator + "...]";
    }
    protected abstract String getItemPlaceholder();

    protected List<E> parse(String data) throws ParseException {
        List<E> ret = new ArrayList<E>();
        for (String s : data.split(getSeparator(), -1))
            ret.add(parseItem(s));
        return ret;
    }
    protected abstract E parseItem(String data);

}
