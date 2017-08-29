package net.instant.util.argparse;

public class OptionValue<X> {

    private final BaseOption<X> option;
    private final X value;

    public OptionValue(BaseOption<X> option, X value) {
        this.option = option;
        this.value = value;
    }

    public BaseOption<X> getOption() {
        return option;
    }

    public X getValue() {
        return value;
    }

    public OptionValue<X> merge(OptionValue<X> old) {
        return this;
    }

}
