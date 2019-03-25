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
        SHORT_OPTION(true, false), // Single-letter option
        LONG_OPTION(true, false),  // Long option
        VALUE(false, true),        // Value immediately following an option
        ARGUMENT(false, true),     // Stand-alone argument
        SPECIAL(false, false);     // A special token, like the "--" delimiter

        private final boolean option;
        private final boolean argument;

        private ArgType(boolean option, boolean argument) {
            this.option = option;
            this.argument = argument;
        }

        public boolean isOption() {
            return option;
        }

        public boolean isArgument() {
            return argument;
        }

        public boolean matches(Mode mode) {
            if (this == SPECIAL) return true;
            switch (mode) {
                case OPTIONS:
                    return option;
                case ARGUMENTS:
                case FORCE_ARGUMENTS:
                    return argument;
                default:
                    throw new AssertionError("This should not happen!");
            }
        }

    }

    public static class ArgValue {

        private final ArgType type;
        private final String value;

        public ArgValue(ArgType type, String value) {
            this.type = type;
            this.value = value;
        }

        public String toString() {
            switch (type) {
                case SHORT_OPTION: return "option -"      +       value ;
                case LONG_OPTION : return "option --"     +       value ;
                case VALUE       : return "option value " + quote(value);
                case ARGUMENT    : return "argument "     + quote(value);
                case SPECIAL     : return "token "        +       value ;
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
            return Converter.quote(value);
        }

    }

    private final Iterator<String> iterator;
    private String value;
    /* Zero     -- at beginning/end of value
     * Positive -- parsing short options; next at this index
     * Negative -- parsing long option; value at negative of this index */
    private int index;
    private int nextIndex;
    private ArgValue peekValue;

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
        if (peekValue != null || index != 0 && index < value.length())
            return true;
        return iterator.hasNext();
    }

    public ArgValue peek(Mode mode) {
        if (peekValue != null && peekValue.getType().matches(mode)) {
            /* We can reuse the last peeked-at value */
            return peekValue;
        }
        if (value == null) {
            /* New value required */
            if (! iterator.hasNext())
                return null;
            value = iterator.next();
            if (value == null)
                throw new NullPointerException("Null arguments not allowed");
            index = 0;
        }
        ArgType tp;
        String v;
        if (mode != Mode.FORCE_ARGUMENTS && index == 0) {
            /* Beginning of an option or argument */
            if (value.equals("-") || value.equals("--")) {
                /* Special case */
                tp = (value.equals("-")) ? ArgType.ARGUMENT : ArgType.SPECIAL;
                v = value;
                nextIndex = 0;
            } else if (value.startsWith("--")) {
                /* Long option */
                tp = ArgType.LONG_OPTION;
                nextIndex = value.indexOf('=') + 1;
                if (nextIndex != 0) {
                    v = value.substring(2, nextIndex - 1);
                    nextIndex = -nextIndex;
                } else {
                    v = value.substring(2);
                }
            } else if (value.startsWith("-")) {
                /* Short options */
                tp = ArgType.SHORT_OPTION;
                v = value.substring(1, 2);
                nextIndex = 2;
            } else {
                /* Just an argument */
                tp = ArgType.ARGUMENT;
                v = value;
                nextIndex = 0;
            }
        } else if (mode != Mode.OPTIONS || index < 0) {
            /* A (possibly directly attached) argument */
            tp = (index != 0) ? ArgType.VALUE : ArgType.ARGUMENT;
            v = value.substring(Math.abs(index));
            nextIndex = 0;
        } else {
            /* More short options */
            tp = ArgType.SHORT_OPTION;
            v = value.substring(index, index + 1);
            nextIndex = index + 1;
        }
        /* Create value; done */
        peekValue = new ArgValue(tp, v);
        return peekValue;
    }

    public ArgValue next(Mode mode) {
        /* Ensure we are in the correct state; finish if at end */
        ArgValue ret = peek(mode);
        if (ret == null) return null;
        /* Prepare for advancing iterator if necessary */
        index = nextIndex;
        if (index == 0 || index == value.length()) value = null;
        /* Consume peekValue; done */
        peekValue = null;
        return ret;
    }

}
