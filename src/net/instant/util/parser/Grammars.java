package net.instant.util.parser;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.instant.util.LineColumnReader;
import net.instant.util.NamedValue;

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
                        "\\\\(?:[\\\\\"/]|u[0-9a-fA-F]{4}|U[0-9a-fA-F]{8})"),
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
                prod("ProductionContent", nt("Symbol", SYM_OPTIONAL)),
                prod("ProductionContent", nt("Symbol"), nt("S", SYM_DISCARD),
                     nt("Bar", SYM_DISCARD), nt("LBS", SYM_DISCARD),
                     nt("ProductionContent", SYM_INLINE)),
                prod("Production", nt("Identifier"), nt("LBS", SYM_DISCARD),
                     nt("Equals", SYM_DISCARD), nt("S", SYM_DISCARD),
                     nt("ProductionContent"),
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
                prod("SectionList"),
                prod("SectionList", nt("Section"),
                     nt("SectionList", SYM_INLINE | SYM_OPTIONAL)),
                prod("File", nt("SectionContent"), nt("SectionList")),
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

    private static class MapperHolder {

        public static class NamedGrammar implements NamedValue {

            private final String name;
            private final Grammar grammar;

            public NamedGrammar(String name, Grammar grammar) {
                this.name = name;
                this.grammar = grammar;
            }

            public String getName() {
                return name;
            }

            public Grammar getGrammar() {
                return grammar;
            }

        }

        public static final UnionMapper<String> STRING_ELEMENT =
            new UnionMapper<String>();

        public static final Mapper<String> STRING =
            new CompositeMapper<String, String>(STRING_ELEMENT) {
                protected String mapInner(Parser.ParseTree pt,
                                          List<String> children) {
                    StringBuilder sb = new StringBuilder();
                    for (String s : children) sb.append(s);
                    return sb.toString();
                }
            };

        public static final UnionMapper<Integer> SYMBOL_FLAG =
            new UnionMapper<Integer>();

        public static final UnionMapper<Grammar.Symbol> SYMBOL_CONTENT =
            new UnionMapper<Grammar.Symbol>();

        public static final Mapper<Grammar.Symbol> SYMBOL =
            new Mapper<Grammar.Symbol>() {
                public Grammar.Symbol map(Parser.ParseTree pt) {
                    Grammar.Symbol base = null;
                    int flags = 0;
                    for (Parser.ParseTree child : pt.getChildren()) {
                        if (SYMBOL_FLAG.canMap(child)) {
                            flags |= SYMBOL_FLAG.map(child);
                            continue;
                        }
                        if (base != null)
                            throw new IllegalArgumentException(
                                "Incorrect redundant symbol content");
                        base = SYMBOL_CONTENT.map(child);
                    }
                    if (flags == 0) return base;
                    return new Grammar.Symbol(base.getType(),
                                              base.getContent(), flags);
                }
            };

        public static final Mapper<String> PRODUCTION_NAME =
            LeafMapper.string();

        public static final Mapper<List<Grammar.Symbol>> PRODUCTION_CONTENT =
            CompositeMapper.aggregate(SYMBOL);

        public static final Mapper<Grammar.Production> PRODUCTION =
            new RecordMapper<Grammar.Production>() {

                private final Mapper<String> NAME = add(LeafMapper.string());
                private final Mapper<List<Grammar.Symbol>> CONTENT =
                    add(CompositeMapper.aggregate(SYMBOL));

                protected Grammar.Production mapInner(Result res) {
                    return new Grammar.Production(res.get(NAME),
                                                  res.get(CONTENT));
                }

            };

        public static final Mapper<Grammar> GRAMMAR = new CompositeMapper<
                    Grammar.Production, Grammar>(PRODUCTION) {
                protected Grammar mapInner(Parser.ParseTree pt,
                        List<Grammar.Production> children) {
                    return new Grammar(children);
                }
            };

        public static final Mapper<NamedGrammar> SECTION =
            new RecordMapper<NamedGrammar>() {

                private final Mapper<String> NAME = add(LeafMapper.string());
                private final Mapper<Grammar> CONTENT =
                    add(GRAMMAR);

                protected NamedGrammar mapInner(Result res) {
                    return new NamedGrammar(res.get(NAME), res.get(CONTENT));
                }

            };

        public static final Mapper<Map<String, Grammar>> FILE =
            new RecordMapper<Map<String, Grammar>>() {

                private final Mapper<Grammar> HEADER = add(GRAMMAR);
                private final Mapper<List<NamedGrammar>> BODY =
                    add(CompositeMapper.aggregate(SECTION));

                protected Map<String, Grammar> mapInner(Result res) {
                    Map<String, Grammar> ret =
                        new LinkedHashMap<String, Grammar>();
                    Grammar header = res.get(HEADER);
                    if (! header.isEmpty())
                        ret.put(null, header);
                    for (NamedGrammar ng : res.get(BODY)) {
                        ret.put(ng.getName(), ng.getGrammar());
                    }
                    return ret;
                }

            };

        public static final Mapper<Map<String, Grammar>> FILE_WRAPPER =
            RecordMapper.wrap(FILE);

        public static final Mapper<Parser.ParserGrammar> PARSER =
            new RecordMapper.WrapperMapper<Map<String, Grammar>,
                                           Parser.ParserGrammar>(FILE) {
                protected Parser.ParserGrammar process(
                        Map<String, Grammar> value) {
                    Grammar lexer = value.get("tokens");
                    Grammar parser = value.get("grammar");
                    for (String k : value.keySet()) {
                        if ("tokens".equals(k) || "grammar".equals(k))
                            continue;
                        if (k == null)
                            throw new IllegalArgumentException("Parser " +
                                "grammar files should not contain headers");
                        throw new IllegalArgumentException(
                            "Unrecognized grammar file section " + k);
                    }
                    if (lexer == null)
                        throw new IllegalArgumentException(
                            "Missing token definition in grammar file");
                    if (parser == null)
                        throw new IllegalArgumentException(
                            "Missing grammar definition in grammar file");
                    return new Parser.ParserGrammar(lexer, parser);
                }
            };

        static {
            STRING_ELEMENT.add("StringContent", LeafMapper.string());
            STRING_ELEMENT.add("RegexContent", LeafMapper.string());
            STRING_ELEMENT.add("Escape", new LeafMapper<String>() {
                protected String mapInner(Parser.ParseTree pt) {
                    String data = pt.getContent();
                    if (data.charAt(1) == 'u' || data.charAt(1) == 'U')
                        return new String(Character.toChars(Integer.parseInt(
                            data.substring(2), 16)));
                    return Character.toString(data.charAt(1));
                }
            });

            SYMBOL_FLAG.add("Caret",
                            LeafMapper.constant(Grammar.SYM_INLINE));
            SYMBOL_FLAG.add("Tilde",
                            LeafMapper.constant(Grammar.SYM_DISCARD));
            SYMBOL_FLAG.add("Question",
                            LeafMapper.constant(Grammar.SYM_OPTIONAL));
            SYMBOL_FLAG.add("Plus",
                            LeafMapper.constant(Grammar.SYM_REPEAT));

            SYMBOL_CONTENT.add("Identifier",
                new LeafMapper<Grammar.Symbol>() {
                    protected Grammar.Symbol mapInner(Parser.ParseTree pt) {
                        return Grammar.Symbol.nonterminal(pt.getContent());
                    }
                });
            SYMBOL_CONTENT.add("String",
                new Mapper<Grammar.Symbol>() {
                    public Grammar.Symbol map(Parser.ParseTree pt) {
                        return Grammar.Symbol.terminal(STRING.map(pt));
                    }
                });
            SYMBOL_CONTENT.add("Regex",
                new Mapper<Grammar.Symbol>() {
                    public Grammar.Symbol map(Parser.ParseTree pt) {
                        return Grammar.Symbol.pattern(STRING.map(pt));
                    }
                });
            SYMBOL_CONTENT.add("Asterisk",
                LeafMapper.constant(Grammar.Symbol.anything()));
        }

    }

    private Grammars() {}

    public static Parser.CompiledGrammar getMetaGrammar() {
        return MetaGrammar.COMPILED_INSTANCE;
    }

    public static Mapper<Grammar> getGrammarContentMapper() {
        return MapperHolder.GRAMMAR;
    }
    public static Mapper<Map<String, Grammar>> getGrammarFileMapper() {
        return MapperHolder.FILE_WRAPPER;
    }
    public static Mapper<Parser.ParserGrammar> getParserMapper() {
        return MapperHolder.PARSER;
    }

    public static Parser.ParserGrammar parseGrammar(Reader input)
            throws Parser.ParsingException {
        return getParserMapper().map(getMetaGrammar().makeParser(input)
            .parse());
    }
    public static Parser.ParserGrammar parseGrammar(LineColumnReader input)
            throws Parser.ParsingException {
        return getParserMapper().map(getMetaGrammar().makeParser(input)
            .parse());
    }

}
