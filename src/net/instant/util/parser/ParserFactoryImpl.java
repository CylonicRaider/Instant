package net.instant.util.parser;

import java.io.Reader;
import net.instant.api.parser.CompiledGrammar;
import net.instant.api.parser.Grammar;
import net.instant.api.parser.GrammarView;
import net.instant.api.parser.InvalidGrammarException;
import net.instant.api.parser.Mapper;
import net.instant.api.parser.ParserFactory;
import net.instant.api.parser.ParsingException;
import net.instant.util.LineColumnReader;

public class ParserFactoryImpl implements ParserFactory {

    public static final ParserFactoryImpl INSTANCE = new ParserFactoryImpl();

    public Grammar createGrammar() {
        return new GrammarImpl();
    }
    public Grammar createGrammar(GrammarView copyFrom) {
        return new GrammarImpl(copyFrom);
    }

    public CompiledGrammar compile(GrammarView base)
            throws InvalidGrammarException {
        return ParserImpl.compile(new GrammarImpl(base));
    }

    public Lexer createTokenSource(Reader input) {
        return new Lexer(new LineColumnReader(input));
    }

    public CompiledGrammar getMetaGrammar() {
        return Grammars.getMetaGrammar();
    }
    public Mapper<Grammar> getGrammarMapper() {
        return Grammars.getGrammarFileMapper();
    }
    public Grammar parseGrammar(Reader input) throws ParsingException {
        return Grammars.parseGrammar(input);
    }

}
