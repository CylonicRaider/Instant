package net.instant.util.argparse;

public class OptionValue<T> {

    private final Option<T> option;
    private final T value;

    public OptionValue(Option<T> option, T value) {
        this.option = option;
        this.value = value;
    }

    public Option<T> getOption() {
        return option;
    }
    public T getValue() {
        return value;
    }

}
