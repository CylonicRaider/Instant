package net.instant.util.argparse;

import java.util.Iterator;

/* FIXME: Non-BMP Unicode support */
public class ArgumentSplitter {

    protected final Iterator<String> iterator;
    protected String value;
    /* Zero -- nothing consumed; grab new value
     * Positive -- parsing short options; next at this index
     * Negative -- parsing long option; next at negative of this index
     */
    protected int index;

    public ArgumentSplitter(Iterable<String> args) {
        iterator = args.iterator();
    }

    public ArgValue next(boolean expectArgument) {
        if (index == 0 || index >= value.length()) {
            /* New value required */
            if (! iterator.hasNext()) return null;
            value = iterator.next();
            index = 0;
        }
        if (index == 0) {
            /* Beginning of an option */
            if (value.equals("-") || value.equals("--")) {
                /* Special case */
                return new ArgValue(ArgValue.Type.ARGUMENT, value);
            } else if (value.startsWith("--")) {
                /* Long option */
                index = value.indexOf('=') + 1;
                if (index != 0) {
                    return new ArgValue(ArgValue.Type.LONG_OPTION,
                                        value.substring(2, index - 1));
                } else {
                    return new ArgValue(ArgValue.Type.LONG_OPTION,
                                        value.substring(2));
                }
            } else if (value.startsWith("-")) {
                /* Short options */
                index = 2;
                return new ArgValue(ArgValue.Type.SHORT_OPTION,
                                    value.substring(2, 3));
            } else {
                /* Just an argument */
                return new ArgValue(ArgValue.Type.ARGUMENT, value);
            }
        } else if (expectArgument || index < 0) {
            /* A (possibly directly attached) argument */
            int idx = Math.abs(index);
            index = 0;
            return new ArgValue((idx != 0) ? ArgValue.Type.VALUE :
                ArgValue.Type.ARGUMENT, value.substring(idx));
        } else {
            /* More short options */
            index++;
            return new ArgValue(ArgValue.Type.SHORT_OPTION,
                                value.substring(index - 1, index));
        }
    }

}
