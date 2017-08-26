package net.instant.util.argparse;

public class OptionValue<X> {

    private final Option<X> option;
    private final X value;

    public OptionValue(Option<X> option, X value) {
        this.option = option;
        this.value = value;
    }

    public Option<X> getOption() {
        return option;
    }

    public X getValue() {
        return value;
    }

}
