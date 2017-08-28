package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.List;

public abstract class ListOption<E> extends ValueOption<List<E>> {

    /* Caution, regular expression pattern. */
    private String separator = ",";
    private String itemPlaceholder;

    public ListOption(String name, Character shortname, String help) {
        super(name, shortname, help);
        itemPlaceholder = getDefaultItemPlaceholder();
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

    public String getItemPlaceholder() {
        return itemPlaceholder;
    }
    public void setItemPlaceholder(String p) {
        itemPlaceholder = p;
    }
    public ListOption<E> withItemPlaceholder(String p) {
        itemPlaceholder = p;
        return this;
    }
    protected abstract String getDefaultItemPlaceholder();

    protected String getDefaultPlaceholder() {
        return getItemPlaceholder() + "[" + separator + "...]";
    }

    protected List<E> parse(String data) throws ParseException {
        List<E> ret = new ArrayList<E>();
        for (String s : data.split(getSeparator(), -1))
            ret.add(parseItem(s));
        return ret;
    }
    protected abstract E parseItem(String data);

}
