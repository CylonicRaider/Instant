package net.instant.api.parser;

import java.io.Closeable;
import java.util.List;
import net.instant.api.NamedValue;

/**
 * A grammar-driven parser.
 * A parser receives tokens from a TokenSource, matches them against a
 * grammar, produces a parse tree (or rejects the input as not matching the
 * grammar), and selects token definitions to be considered by its TokenSource
 * for the next token.
 * Closing a Parser closes the underlying TokenSources.
 * The CompiledGrammar interface contains a factory method for producing
 * Parser-s.
 */
public interface Parser extends Closeable {

    /**
     * A single parse tree node.
     * A parse tree has a name (that is either the name of the underlying
     * token or the name of the grammar production that gave rise to the
     * parse tree), an optional token (present on leaf nodes), and a list of
     * child nodes.
     */
    interface ParseTree extends NamedValue {

        /**
         * The token this ParseTree directly corresponds to, or null.
         * Only leaf nodes may have tokens; given "discarded" symbols in the
         * grammar, some parse tree nodes may have no children (be "false
         * leaves") although they do not correspond to a single token.
         */
        TokenSource.Token getToken();

        /**
         * An immutable list of this node's children.
         */
        List<ParseTree> getChildren();

    }

    /**
     * The CompiledGrammar this parser has been derived from.
     */
    CompiledGrammar getGrammar();

    /**
     * The TokenSourc this parser is obtaining tokens from.
     */
    TokenSource getTokenSource();

    /**
     * Whether the parser is keeping otherwise-discarded tokens in its parse
     * tree.
     */
    boolean isKeepingAll();

    /**
     * Parse the underlying input.
     * A ParsingException is thrown if parsing fails for any reason.
     * Subsequent calls of this method return the same already-created
     * ParseTree (or throw another ParsingException representing the problem
     * the parser is stuck at).
     */
    ParseTree parse() throws ParsingException;

}
