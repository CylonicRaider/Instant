package net.instant.util.parser;

import java.io.Reader;
import net.instant.util.LineColumnReader;

public class ParserFactory {

    public static final ParserFactory INSTANCE = new ParserFactory();

    public Grammar createGrammar() {
        return new Grammar();
    }
    public Grammar createGrammar(GrammarView copyFrom) {
        return new Grammar(copyFrom);
    }

    public Parser.CompiledGrammar compile(GrammarView base)
            throws InvalidGrammarException {
        return Parser.compile(new Grammar(base));
    }

    public Lexer createTokenSource(Reader input) {
        return new Lexer(new LineColumnReader(input));
    }

    public Parser.CompiledGrammar getMetaGrammar() {
        return Grammars.getMetaGrammar();
    }
    public Mapper<Grammar> getGrammarMapper() {
        return Grammars.getGrammarFileMapper();
    }
    public Grammar parseGrammar(Reader input) throws InvalidGrammarException,
                                                     Parser.ParsingException {
        return Grammars.parseGrammar(input);
    }

}
