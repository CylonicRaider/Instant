package net.instant.util.parser;

public class ParserGrammar extends Grammar {

    public static final String START_SYMBOL = "$start";

    private Grammar reference;

    public ParserGrammar() {
        super();
    }
    public ParserGrammar(GrammarView copyFrom) {
        super(copyFrom);
    }
    public ParserGrammar(Production... productions) {
        super(productions);
    }

    public Grammar getReference() {
        return reference;
    }
    public void setReference(Grammar ref) {
        reference = ref;
    }

    protected boolean checkProductions(String name) {
        return (super.checkProductions(name) ||
            (reference != null && reference.checkProductions(name)));
    }
    public void validate() throws InvalidGrammarException {
        validate(START_SYMBOL);
    }

}
