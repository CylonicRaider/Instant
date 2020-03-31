package net.instant.api.parser;

import java.io.Closeable;
import java.util.Map;
import net.instant.api.NamedValue;

/**
 * A dynamically reprogrammable externally driven tokenizer.
 * (Assuming a default implementation,) a TokenSource maintains an input
 * character stream, a current location inside that character stream, a set
 * of token definitions (the *selection*), and a (cached) match result (which
 * may be absent).
 * Upon a peek(), if there is no stored match result, the TokenSource
 * concurrently matches all token definitions from the current selection
 * against the input stream at the current location, stores the result of that
 * match operation, and returns its status code. If the match was successful,
 * the token produced by the match can be retrieved via getCurrentToken(). A
 * subsequent next() call advances the input location such that it points at
 * the end of the most recently matched token and clears the internal match
 * state, preparing for the next peek() call.
 * Closing a TokenSource closes the underlying Reader (if any).
 * The ParserFactory interface provides a factory method for constructing a
 * default TokenSource from a Reader.
 */
public interface TokenSource extends Closeable {

    /**
     * A token is a piece of text associated with a location inside the
     * input stream and a name.
     */
    interface Token extends NamedValue {

        /**
         * The location of this token in the input stream.
         */
        TextLocation getLocation();

        /**
         * The text this token encompasses.
         */
        String getContent();

        /**
         * Test whether this token matches the given symbol.
         * If sym is a nonterminal, the token's name must match the symbol's
         * reference; if sym is a terminal, the token's content must match
         * the symbol's pattern.
         */
        boolean matches(Grammar.Symbol sym);

    }

    /**
     * Interface representing the definition of a class of tokens.
     * This combines a name (from NamedValue) with a match rank and a regular
     * expression defining possible token contents (from TerminalSymbol);
     * the flags of the symbol are presently unused.
     */
    interface TokenPattern extends NamedValue {

        /**
         * The Symbol defining the token category and contents.
         */
        Grammar.TerminalSymbol getSymbol();

        /**
         * Create a Token deriving from this TokenPattern.
         * The token's parameters are taken from this TokenPattern (for the
         * name) and from this method's parameters (for the location and the
         * content).
         */
        Token createToken(TextLocation location, String content);

    }

    /**
     * A set of token definitions.
     * The token definitions must have distinct names; this constraint and
     * the isCompatibleWith() and contains() methods attempt to optimize for
     * the common case of narrowing down a selection to a subset thereof while
     * retaining the current stored match result.
     */
    interface Selection {

        /**
         * The token definitions of this selection.
         * Each TokenPattern must be mapped to from its name (that is, for
         * every Map.Entry<> ent from this map, the invariant
         *
         *     ent.getKey().equals(ent.getValue().getName())
         *
         * must hold).
         */
        Map<String, TokenPattern> getPatterns();

        /**
         * Quickly test whether this token definition set is a subset of
         * other.
         * If that is the case, the TokenSource requested to use this
         * selection may be able to avoid having to re-calculate the current
         * token (but see also contains()).
         */
        boolean isCompatibleWith(Selection other);

        /**
         * Quickly test whether the given token matches a definition inside
         * this Selection.
         * If that is the case, the TokenSource requested to use this
         * selection may be able to avoid having to re-calculate the current
         * token (but see also isCompatibileWith()).
         */
        boolean contains(Token tok);

    }

    /**
     * The result of a token matching operation.
     */
    enum MatchStatus {
        /** Matching successfully resulted in a new token. */
        OK,
        /** No new token could be extracted. */
        NO_MATCH,
        /** The end of the input stream has been reached. */
        EOI
    }

    /**
     * Set the set of token definitions that should be matched against the
     * input stream.
     * If there is a cached match result, it is discarded and peek() can be
     * called to apply the new selection at the same location.
     */
    void setSelection(Selection sel);

    /**
     * Retrieve the current location of the TokenSource in its input stream.
     * The next token produced by this will have that location.
     */
    TextLocation getCurrentLocation();

    /**
     * Retrieve the latest token produced by this TokenSource, or null.
     * If there is no cached match result or that match did not produce a
     * token, this returns null.
     */
    Token getCurrentToken();

    /**
     * Perform a match operation, set internal state fields, and return the
     * match's result.
     * If required is true and a NO_MATCH would have been returned otherwise,
     * or if there are multiple equally applicable token definitions (with no
     * possibility of disambiguation), this raises a MatchingException.
     */
    MatchStatus peek(boolean required) throws MatchingException;

    /**
     * Advance the internal position past the latest token, generating the
     * latter if necessary.
     * If there is no stored match result, this invokes peek(true) (and may,
     * in particular, raise an exception if no token can be constructed at
     * the current position). The token of the latest match operation (or the
     * one produced by the above parenthesis) is returned, or null if the
     * end of input is reached.
     */
    Token next() throws MatchingException;

}
