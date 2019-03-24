package net.instant.util.argparse;

import java.util.Iterator;

/* FIXME: Add non-BMP Unicode support */
public class ArgumentSplitter implements Iterable<String> {

    public enum Mode {
        OPTIONS,        // Continuations of short options are options, too
        ARGUMENTS,      // Continuations of short options are arguments
        FORCE_ARGUMENTS // Everything is an argument
    }

    public enum ArgType {
        SHORT_OPTION, // Single-letter option
        LONG_OPTION,  // Long option
        VALUE,        // Value immediately following an option
        ARGUMENT      // Stand-alone argument
    }

    public static class ArgValue {

        private final ArgType type;
        private final String value;

        public ArgValue(ArgType type, String value) {
            this.type = type;
            this.value = value;
        }

        public String toString() {
            switch (getType()) {
                case SHORT_OPTION: return "option -"      +       value ;
                case LONG_OPTION : return "option --"     +       value ;
                case VALUE       : return "option value " + quote(value);
                case ARGUMENT    : return "argument "     + quote(value);
                default: throw new AssertionError("This should not happen!");
            }
        }

        public ArgType getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        private static String quote(String value) {
            return (value.contains("'")) ? '"' + value + '"' :
                                           "'" + value + "'";
        }

    }

    protected final Iterator<String> iterator;
    protected String value;
    /* Zero     -- nothing consumed; grab new value
     * Positive -- parsing short options; next at this index
     * Negative -- parsing long option; value at negative of this index */
    protected int index;
    protected ArgumentSplitter.ArgValue pushbackValue;

    public ArgumentSplitter(Iterable<String> args) {
        iterator = args.iterator();
    }

    public Iterator<String> iterator() {
        return new Iterator<String>() {

            public boolean hasNext() {
                return ArgumentSplitter.this.hasNext();
            }

            public String next() {
                return ArgumentSplitter.this.next(
                    Mode.FORCE_ARGUMENTS).getValue();
            }

            public void remove() {
                // While we *could* remove from the underlying iterator,
                // it is not clear how this should interact with pushback.
                throw new UnsupportedOperationException("Cannot remove " +
                    "from ArgumentSplitter");
            }

        };
    }

    public boolean hasNext() {
        if (pushbackValue != null || index != 0 && index < value.length())
            return true;
        return iterator.hasNext();
    }

    public ArgumentSplitter.ArgValue next(Mode mode) {
        if (pushbackValue != null) {
            /* Argument has been peeked at or rejected */
            ArgumentSplitter.ArgValue ret = pushbackValue;
            pushbackValue = null;
            return ret;
        }
        if (index == 0 || index >= value.length()) {
            /* New value required */
            if (! iterator.hasNext()) return null;
            value = iterator.next();
            index = 0;
        }
        ArgType tp;
        String v;
        if (mode != Mode.FORCE_ARGUMENTS && index == 0) {
            /* Beginning of an option */
            if (value.equals("-") || value.equals("--")) {
                /* Special case */
                return new ArgumentSplitter.ArgValue(ArgType.ARGUMENT, value);
            } else if (value.startsWith("--")) {
                /* Long option */
                tp = ArgType.LONG_OPTION;
                index = value.indexOf('=') + 1;
                if (index != 0) {
                    v = value.substring(2, index - 1);
                    index = -index;
                } else {
                    v = value.substring(2);
                }
            } else if (value.startsWith("-")) {
                /* Short options */
                tp = ArgType.SHORT_OPTION;
                v = value.substring(1, 2);
                index = 2;
            } else {
                /* Just an argument */
                tp = ArgType.ARGUMENT;
                v = value;
            }
        } else if (mode != Mode.OPTIONS || index < 0) {
            /* A (possibly directly attached) argument */
            tp = (index != 0) ? ArgType.VALUE : ArgType.ARGUMENT;
            v = value.substring(Math.abs(index));
        } else {
            /* More short options */
            tp = ArgType.SHORT_OPTION;
            v = value.substring(index, index + 1);
            index++;
        }
        return new ArgumentSplitter.ArgValue(tp, v);
    }

    public ArgumentSplitter.ArgValue peek(Mode mode) {
        ArgumentSplitter.ArgValue ret = next(mode);
        pushback(ret);
        return ret;
    }

    public void pushback(ArgumentSplitter.ArgValue value) {
        pushbackValue = value;
    }

}
