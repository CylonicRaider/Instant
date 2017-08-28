package net.instant.util.argparse;

import java.util.Collection;

/* Y must be modifiable. */
public class ListOptionValue<X, Y extends Collection<X>>
        extends OptionValue<Y> {

    public ListOptionValue(Option<Y> option, Y value) {
        super(option, value);
    }

    public OptionValue<Y> merge(OptionValue<Y> old) {
        old.getValue().addAll(getValue());
        return old;
    }

}
