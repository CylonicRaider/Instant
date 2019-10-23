package net.instant.util.parser;

public class ParserGrammar extends Grammar {

    private Grammar reference;

    public ParserGrammar() {
        super();
    }
    public ParserGrammar(Grammar copyFrom) {
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

}
