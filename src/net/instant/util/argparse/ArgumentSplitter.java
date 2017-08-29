package net.instant.util.argparse;

import java.util.Iterator;

/* FIXME: Non-BMP Unicode support */
public class ArgumentSplitter implements Iterable<String> {

    public enum Mode {
        OPTIONS, // Continuations of short options are options, too
        ARGUMENTS, // Continuations of short options are arguments
        FORCE_ARGUMENTS // Everything is an argument
    }

    protected final Iterator<String> iterator;
    protected String value;
    /* Zero -- nothing consumed; grab new value
     * Positive -- parsing short options; next at this index
     * Negative -- parsing long option; next at negative of this index
     */
    protected int index;
    protected ArgumentValue pushbackValue;

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

    public ArgumentValue next(Mode mode) {
        if (pushbackValue != null) {
            /* Argument percolating back up the call chain */
            ArgumentValue ret = pushbackValue;
            pushbackValue = null;
            return ret;
        }
        if (index == 0 || index >= value.length()) {
            /* New value required */
            if (! iterator.hasNext()) return null;
            value = iterator.next();
            index = 0;
        }
        if (mode != Mode.FORCE_ARGUMENTS && index == 0) {
            /* Beginning of an option */
            if (value.equals("-") || value.equals("--")) {
                /* Special case */
                return new ArgumentValue(ArgumentValue.Type.ARGUMENT, value);
            } else if (value.startsWith("--")) {
                /* Long option */
                index = value.indexOf('=') + 1;
                if (index != 0) {
                    return new ArgumentValue(ArgumentValue.Type.LONG_OPTION,
                                             value.substring(2, index - 1));
                } else {
                    return new ArgumentValue(ArgumentValue.Type.LONG_OPTION,
                                             value.substring(2));
                }
            } else if (value.startsWith("-")) {
                /* Short options */
                index = 2;
                return new ArgumentValue(ArgumentValue.Type.SHORT_OPTION,
                                    value.substring(2, 3));
            } else {
                /* Just an argument */
                return new ArgumentValue(ArgumentValue.Type.ARGUMENT, value);
            }
        } else if (mode != Mode.OPTIONS || index < 0) {
            /* A (possibly directly attached) argument */
            int idx = Math.abs(index);
            index = 0;
            return new ArgumentValue((idx != 0) ? ArgumentValue.Type.VALUE :
                ArgumentValue.Type.ARGUMENT, value.substring(idx));
        } else {
            /* More short options */
            index++;
            return new ArgumentValue(ArgumentValue.Type.SHORT_OPTION,
                                     value.substring(index - 1, index));
        }
    }

    public void pushback(ArgumentValue value) {
        pushbackValue = value;
    }

}
