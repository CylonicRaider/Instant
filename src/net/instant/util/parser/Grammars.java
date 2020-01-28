package net.instant.util.parser;

import java.io.Reader;
import net.instant.util.LineColumnReader;

public final class Grammars {

    private static class MetaGrammar extends Parser.ParserGrammar {

        private static final String ILS =
            Lexer.LexerGrammar.START_SYMBOL.getContent();
        private static final String IPS = START_SYMBOL.getContent();

        public static final MetaGrammar INSTANCE;
        public static final Parser.CompiledGrammar COMPILED_INSTANCE;

        static {
            INSTANCE = new MetaGrammar();
            try {
                COMPILED_INSTANCE = Parser.compile(INSTANCE);
            } catch (InvalidGrammarException exc) {
                throw new RuntimeException(exc);
            }
        }

        public MetaGrammar() {
            super(
                /* Lexer grammar */
                new Grammar(
                    /* Token types */
                    Lexer.terminalToken("CR", "\r"),
                    Lexer.terminalToken("LF", "\n"),
                    Lexer.patternToken("SP", "[ \t\u000b\f]+"),
                    Lexer.terminalToken("BracketOpen", "["),
                    Lexer.terminalToken("BracketClose", "]"),
                    Lexer.terminalToken("Equals", "="),
                    Lexer.terminalToken("Bar", "|"),
                    Lexer.terminalToken("Quote", "\""),
                    Lexer.terminalToken("Slash", "/"),
                    Lexer.terminalToken("Asterisk", "*"),
                    Lexer.patternToken("StartComment", "[#;]"),
                    Lexer.terminalToken("Caret", "^"),
                    Lexer.terminalToken("Tilde", "~"),
                    Lexer.terminalToken("Question", "?"),
                    Lexer.terminalToken("Plus", "+"),
                    Lexer.patternToken("Identifier",
                        "[a-zA-Z$](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?"),
                    Lexer.patternToken("StringContent", "[^\"\\\\]+"),
                    Lexer.patternToken("RegexContent", "[^/\\\\]+"),
                    Lexer.patternToken("CommentContent", "[^\r\n]+"),
                    Lexer.patternToken("Escape",
                        "\\\\(?:[^uU]|u[0-9a-fA-F]{4}|U[0-9a-fA-F]{8})"),
                    /* Initial state */
                    Lexer.state(ILS, "CR", ILS),
                    Lexer.state(ILS, "LF", ILS),
                    Lexer.state(ILS, "SP", ILS),
                    Lexer.state(ILS, "BracketOpen", ILS),
                    Lexer.state(ILS, "BracketClose", ILS),
                    Lexer.state(ILS, "Equals", ILS),
                    Lexer.state(ILS, "Bar", ILS),
                    Lexer.state(ILS, "Quote", "LString"),
                    Lexer.state(ILS, "Slash", "LRegex"),
                    Lexer.state(ILS, "Asterisk", ILS),
                    Lexer.state(ILS, "StartComment", "LComment"),
                    Lexer.state(ILS, "Caret", ILS),
                    Lexer.state(ILS, "Tilde", ILS),
                    Lexer.state(ILS, "Question", ILS),
                    Lexer.state(ILS, "Plus", ILS),
                    Lexer.state(ILS, "Identifier", ILS),
                    Lexer.state(ILS),
                    /* String state */
                    Lexer.state("LString", "StringContent", "LString"),
                    Lexer.state("LString", "Escape", "LString"),
                    Lexer.state("LString", "Quote", ILS),
                    /* Regex state */
                    Lexer.state("LRegex", "RegexContent", "LRegex"),
                    Lexer.state("LRegex", "Escape", "LRegex"),
                    Lexer.state("LRegex", "Quote", ILS),
                    /* Comment state */
                    Lexer.state("LComment", "CommentContent", "LComment"),
                    Lexer.state("LComment", "CR", ILS),
                    Lexer.state("LComment", "LF", ILS),
                    Lexer.state("LComment")
                ),
                /* Whitespace and comments */
                prod("S", nt("SP", SYM_OPTIONAL)),
                prod("EOL", nt("CR")),
                prod("EOL", nt("LF")),
                prod("EOL", nt("CR"), nt("LF")),
                prod("Comment", nt("StartComment"), nt("CommentContent"),
                     nt("EOL")),
                prod("EOLX", nt("EOL")),
                prod("EOLX", nt("Comment")),
                prod("SEOLX", nt("S"), nt("EOLX")),
                prod("LBS", nt("S")),
                prod("LBS", nt("S"), nt("EOLX"), nt("LBS", SYM_INLINE)),
                /* Symbol definitions */
                prod("SymbolTrailer"),
                prod("SymbolTrailer", nt("Question"),
                     nt("SymbolTrailer", SYM_INLINE)),
                prod("SymbolTrailer", nt("Plus"),
                    nt("SymbolTrailer", SYM_INLINE)),
                prod("String"),
                prod("String", nt("StringContent"), nt("String", SYM_INLINE)),
                prod("String", nt("Escape"), nt("String", SYM_INLINE)),
                prod("Regex"),
                prod("Regex", nt("RegexContent"), nt("Regex", SYM_INLINE)),
                prod("Regex", nt("Escape"), nt("Regex", SYM_INLINE)),
                prod("Symbol", nt("Identifier"),
                     nt("SymbolTrailer", SYM_INLINE)),
                prod("Symbol", nt("Quote", SYM_DISCARD),
                     nt("String"), nt("Quote", SYM_DISCARD),
                     nt("SymbolTrailer", SYM_INLINE)),
                prod("Symbol", nt("Slash", SYM_DISCARD),
                     nt("Regex"), nt("Slash", SYM_DISCARD),
                     nt("SymbolTrailer", SYM_INLINE)),
                prod("Symbol", nt("Asterisk"),
                     nt("SymbolTrailer", SYM_INLINE)),
                prod("Symbol", nt("Caret"), nt("Symbol", SYM_INLINE)),
                prod("Symbol", nt("Tilde"), nt("Symbol", SYM_INLINE)),
                /* Production definitions */
                prod("ProductionContent", nt("Symbol")),
                prod("ProductionContent", nt("Symbol"), nt("S", SYM_DISCARD),
                     nt("Bar", SYM_DISCARD), nt("LBS", SYM_DISCARD),
                     nt("ProductionContent", SYM_INLINE)),
                prod("Production", nt("Identifier"), nt("LBS", SYM_DISCARD),
                     nt("Equals", SYM_DISCARD), nt("S", SYM_DISCARD),
                     nt("ProductionContent", SYM_INLINE | SYM_OPTIONAL),
                     nt("EOLX", SYM_DISCARD)),
                /* File sections */
                prod("SectionHeader",
                     nt("BracketOpen", SYM_DISCARD), nt("S", SYM_DISCARD),
                     nt("Identifier"), nt("S", SYM_DISCARD),
                     nt("BracketClose", SYM_DISCARD),
                     nt("SEOLX", SYM_DISCARD)),
                prod("SectionContentLine", nt("SEOLX", SYM_DISCARD)),
                prod("SectionContentLine", nt("Production")),
                prod("SectionContent",
                     nt("SectionContentLine", SYM_INLINE | SYM_OPTIONAL |
                                              SYM_REPEAT)),
                prod("Section", nt("SectionHeader"), nt("SectionContent")),
                /* Overall file structure */
                prod("File", nt("SectionContent"),
                     nt("Section", SYM_OPTIONAL | SYM_REPEAT)),
                prod(IPS, nt("File"))
            );
        }

        private static Symbol nt(String name) {
            return Symbol.nonterminal(name);
        }
        private static Symbol nt(String name, int flags) {
            return Symbol.nonterminal(name, flags);
        }

        private static Production prod(String name, Symbol... symbols) {
            return new Production(name, symbols);
        }

    }

    private Grammars() {}

    public static Parser.CompiledGrammar getMetaGrammar() {
        return MetaGrammar.COMPILED_INSTANCE;
    }

    public static Parser makeGrammarParser(Reader input) {
        return getMetaGrammar().makeParser(input);
    }
    public static Parser makeGrammarParser(LineColumnReader input) {
        return getMetaGrammar().makeParser(input);
    }

}
