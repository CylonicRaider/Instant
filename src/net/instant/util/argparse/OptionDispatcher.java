package net.instant.util.argparse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OptionDispatcher implements MultiProcessor {

    private final Map<String, Processor> options;
    private final Map<Character, Processor> shortOptions;
    private final List<Processor> arguments;
    private String name;
    private String description;

    public OptionDispatcher(String name, String description) {
        this.options = new LinkedHashMap<String, Processor>();
        this.shortOptions = new LinkedHashMap<Character, Processor>();
        this.arguments = new ArrayList<Processor>();
        this.name = name;
        this.description = description;
    }

    public Map<String, Processor> getOptions() {
        return options;
    }

    public Map<Character, Processor> getShortOptions() {
        return shortOptions;
    }

    public List<Processor> getArguments() {
        return arguments;
    }

    public List<Processor> getAllOptions() {
        List<Processor> ret = new ArrayList<Processor>();
        ret.addAll(getOptions().values());
        ret.addAll(getArguments());
        return ret;
    }

    public String getName() {
        return name;
    }
    public void setName(String n) {
        name = n;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String desc) {
        description = desc;
    }

    public void parse(ArgumentSplitter source, ParseResultBuilder drain)
            throws ParsingException {
        Set<Processor> notSeen = new HashSet<Processor>(getAllOptions());
        Iterator<Processor> nextArg = getArguments().iterator();
        boolean maybeFinal = true;
        for (;;) {
            ArgumentValue av = source.next(ArgumentSplitter.Mode.OPTIONS);
            if (maybeFinal && av == null) {
                for (Processor p : notSeen) {
                    p.parse(source, drain);
                }
                return;
            } else {
                maybeFinal = false;
            }
            source.pushback(av);
            Processor chain;
            switch (av.getType()) {
                case SHORT_OPTION:
                    chain = shortOptions.get(av.getValue().charAt(0));
                    break;
                case LONG_OPTION:
                    chain = options.get(av.getValue());
                    break;
                case VALUE:
                    throw new ParsingException("Orphan " + av);
                case ARGUMENT:
                    if (! nextArg.hasNext())
                        throw new ParsingException("Superfluous " + av);
                    chain = nextArg.next();
                    if (chain == null)
                        throw new NullPointerException("Null argument " +
                            "processor in OptionDispatcher");
                    break;
                default:
                    throw new AssertionError("Unknown argument type " +
                        av.getType() + "?!");
            }
            if (chain == null)
                throw new ParsingException("Unrecognized " + av);
            notSeen.remove(chain);
            chain.parse(source, drain);
        }
    }

    public String formatName() {
        return getName();
    }

    public String formatUsage() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Processor p : getAllOptions()) {
            String pu = p.formatUsage();
            if (pu == null) {
                continue;
            } else if (first) {
                first = false;
            } else {
                sb.append(' ');
            }
            sb.append(pu);
        }
        return (first) ? null : sb.toString();
    }

    public String formatDescription() {
        return getDescription();
    }

    public HelpLine getHelpLine() {
        return null;
    }

    static void accumulateHelpLines(Processor p, List<HelpLine> drain) {
        HelpLine h = p.getHelpLine();
        if (h != null)
            drain.add(h);
        if (p instanceof MultiProcessor)
            drain.addAll(((MultiProcessor) p).getAllHelpLines());
    }
    public List<HelpLine> getAllHelpLines() {
        List<HelpLine> ret = new ArrayList<HelpLine>();
        for (Processor p : getAllOptions()) accumulateHelpLines(p, ret);
        return ret;
    }

}
