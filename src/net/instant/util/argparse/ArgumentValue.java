package net.instant.util.argparse;

public class ArgumentValue {

    public enum Type {
        SHORT_OPTION, // Single-letter option
        LONG_OPTION, // Long option
        VALUE, // Value immediately following an option
        ARGUMENT // Stand-alone argument
    }

    private final Type type;
    private final String value;

    public ArgumentValue(Type type, String value) {
        this.type = type;
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

}
