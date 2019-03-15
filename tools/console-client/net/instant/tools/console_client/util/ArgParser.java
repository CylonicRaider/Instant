package net.instant.tools.console_client.util;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArgParser {

    public static class ParsingException extends Exception {

        public ParsingException() {
            super();
        }
        public ParsingException(String message) {
            super(message);
        }
        public ParsingException(Throwable cause) {
            super(cause);
        }
        public ParsingException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    public abstract class BaseOption {

        private final String name;
        private final Character letter;
        private final String valueDesc;
        private final String description;

        public BaseOption(String name, Character letter, String valueDesc,
                          String description) {
            this.name = name;
            this.letter = letter;
            this.valueDesc = valueDesc;
            this.description = description;
        }

        public String getName() {
            return name;
        }
        public Character getLetter() {
            return letter;
        }
        public String getValueDesc() {
            return valueDesc;
        }
        public String getDescription() {
            return description;
        }

        public abstract String getFullName();
        public abstract String getFullLabel();

    }

    public class Option extends BaseOption {

        public Option(String name, Character letter, String valueDesc,
                      String description) {
            super(name, letter, valueDesc, description);
        }

        public String getFullName() {
            return "option --" + getName();
        }
        public String getFullLabel() {
            StringBuilder sb = new StringBuilder("--");
            sb.append(getName());
            if (getLetter() != null) sb.append("|-").append(getLetter());
            return sb.toString();
        }

        public String parse(Iterator<String> values) throws ParsingException {
            if (getValueDesc() != null) {
                if (! values.hasNext())
                    throw new ParsingException("Missing required value for " +
                        getFullName());
                return values.next();
            } else {
                return null;
            }
        }

    }

    public class HelpOption extends Option {

        public HelpOption(String name, Character letter, String description) {
            super(name, letter, null, description);
        }
        public HelpOption() {
            this("help", '?', "This help.");
        }

        public String parse(Iterator<String> values) throws ParsingException {
            usageAndExit(true, 0);
            // Should not happen.
            return null;
        }

    }

    public class Argument extends BaseOption {

        private final boolean optional;

        public Argument(String name, String valueDesc, boolean optional,
                        String description) {
            super(name, null, valueDesc, description);
            this.optional = optional;
        }
        public Argument(String name, String valueDesc, String description) {
            this(name, valueDesc, false, description);
        }

        public boolean isOptional() {
            return optional;
        }

        public String getFullName() {
            return "argument <" + getName() + ">";
        }
        public String getFullLabel() {
            return "<" + getName() + ">";
        }

        public String parse(String value) throws ParsingException {
            return value;
        }

    }

    private final String programName;
    private final Map<String, Option> options;
    private final Map<Character, Option> letterOptions;
    private final List<Argument> arguments;

    public ArgParser(String programName) {
        this.programName = programName;
        this.options = new LinkedHashMap<String, Option>();
        this.letterOptions = new HashMap<Character, Option>();
        this.arguments = new ArrayList<Argument>();
    }

    public String getProgramName() {
        return programName;
    }

    public void add(Option opt) {
        options.put(opt.getName(), opt);
        if (opt.getLetter() != null) letterOptions.put(opt.getLetter(), opt);
    }

    public void add(Argument arg) {
        arguments.add(arg);
    }

    public void writeUsage(PrintWriter drain) {
        drain.append("USAGE: ").append(programName);
        for (Option opt : options.values()) {
            drain.append(" [");
            drain.append(opt.getFullLabel());
            if (opt.getValueDesc() != null)
                drain.append(" ").append(opt.getValueDesc());
            drain.append("]");
        }
        for (Argument arg : arguments) {
            drain.append(" ");
            if (arg.isOptional()) drain.append("[");
            drain.append("<");
            drain.append(arg.getName());
            drain.append(">");
            if (arg.isOptional()) drain.append("]");
        }
        drain.println();
    }

    public void writeHelp(PrintWriter drain) {
        List<String[]> entries = new ArrayList<String[]>();
        for (Option opt : options.values()) {
            entries.add(new String[] { opt.getFullLabel(), opt.getValueDesc(),
                                       opt.getDescription() });
        }
        for (Argument arg : arguments) {
            entries.add(new String[] { arg.getFullLabel() + ":",
                                       arg.getValueDesc(),
                                       arg.getDescription() });
        }
        int labelWidth = 0, vdescWidth = 0;
        for (String[] ent : entries) {
            int thisLabelWidth = ent[0].length() +
                ((ent[1] != null) ? 1 : 0);
            int thisVDescWidth = (ent[1] != null) ? ent[1].length() : 0;
            labelWidth = Math.max(labelWidth, thisLabelWidth);
            vdescWidth = Math.max(vdescWidth, thisVDescWidth);
        }
        String lineFormat = makeFormat(labelWidth) + makeFormat(vdescWidth) +
            ": %s%n";
        for (String[] ent : entries) {
            if (ent[1] == null) ent[1] = "";
            drain.printf(lineFormat, (Object[]) ent);
        }
    }

    public void usageAndExit(boolean showHelp, int code) {
        PrintWriter pw = new PrintWriter(new BufferedWriter(
            new OutputStreamWriter(System.err)));
        try {
            writeUsage(pw);
            if (showHelp) writeHelp(pw);
        } finally {
            pw.close();
        }
        System.exit(code);
    }

    public Map<String, String> parse(final Iterable<String> values)
            throws ParsingException {
        Map<String, String> ret = new LinkedHashMap<String, String>();
        boolean onlyArguments = false;
        Iterator<String> it = new Iterator<String>() {

            private final Iterator<String> nested = values.iterator();

            public boolean hasNext() {
                return nested.hasNext();
            }

            public String next() {
                return nested.next();
            }

            public void remove() {
                throw new UnsupportedOperationException(
                    "May not remove command-line parameters");
            }

        };
        Iterator<Argument> argsIt = arguments.iterator();
        while (it.hasNext()) {
            String value = it.next();
            if (! onlyArguments && value.startsWith("-") &&
                    value.length() > 1) {
                Option opt;
                if (value.equals("--")) {
                    onlyArguments = true;
                    continue;
                } else if (value.startsWith("--")) {
                    opt = options.get(value.substring(2));
                } else if (value.length() != 2) {
                    opt = null;
                } else {
                    opt = letterOptions.get(value.charAt(1));
                }
                if (opt == null)
                    throw new ParsingException("Unrecognized option " +
                                               value);
                String argument = opt.parse(it);
                ret.put(opt.getName(), argument);
                continue;
            }
            if (! argsIt.hasNext())
                throw new ParsingException("Superfluous argument " + value);
            Argument arg = argsIt.next();
            String result = arg.parse(value);
            ret.put(arg.getName(), result);
        }
        while (argsIt.hasNext()) {
            Argument arg = argsIt.next();
            if (! arg.isOptional())
                throw new ParsingException("Missing value for required " +
                    "argument " + arg.getFullName());
        }
        return ret;
    }

    public Map<String, String> parse(String... values)
            throws ParsingException {
        return parse(Arrays.asList(values));
    }

    private static String makeFormat(int width) {
        return (width == 0) ? "%s" : "%-" + width + "s";
    }

}
