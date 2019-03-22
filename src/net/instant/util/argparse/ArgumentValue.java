package net.instant.util.argparse;

public class ArgumentValue {

    public enum Type {
        SHORT_OPTION, // Single-letter option
        LONG_OPTION,  // Long option
        VALUE,        // Value immediately following an option
        ARGUMENT      // Stand-alone argument
    }

    private final Type type;
    private final String value;

    public ArgumentValue(Type type, String value) {
        this.type = type;
        this.value = value;
    }

    public String toString() {
        switch (getType()) {
            case SHORT_OPTION: return "option -"      +       value ;
            case LONG_OPTION : return "option --"     +       value ;
            case VALUE       : return "option value " + quote(value);
            case ARGUMENT    : return "argument"      + quote(value);
            default: throw new AssertionError("This should not happen!");
        }
    }

    public Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    private static String quote(String value) {
        return (value.contains("'")) ? '"' + value + '"' : "'" + value + "'";
    }

}
