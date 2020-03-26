package net.instant.api.parser;

/**
 * An immutable Grammar from which a Parser can be derived.
 * Created by ParserFactory instances from "plain" GrammarView-s.
 */
public interface CompiledGrammar extends GrammarView {

    /**
     * Create a parser using this grammar and accepting tokens from the given
     * TokenSource.
     * If keepAll is true, tokens corresponding to symbols with the
     * SYM_DISCARD flag set are *not* discarded from the parse tree, such that
     * the entire input sequence can be reconstructed it.
     */
    Parser createParser(TokenSource input, boolean keepAll);

}
