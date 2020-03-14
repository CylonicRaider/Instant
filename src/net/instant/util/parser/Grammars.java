package net.instant.util.parser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.instant.util.Formats;
import net.instant.util.LineColumnReader;

public final class Grammars {

    private static class MetaGrammar extends Parser.ParserGrammar {

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
                /* Tokens */
                patternToken("EndOfLine", "\r?\n|\r"),
                patternToken("Space", "[ \t\u000b\f]+"),
                terminalToken("Equals", "="),
                terminalToken("Bar", "|"),
                terminalToken("Quote", "\""),
                terminalToken("Slash", "/"),
                terminalToken("Percent", "%"),
                patternToken("StartComment", "[#;]"),
                terminalToken("Caret", "^"),
                terminalToken("Tilde", "~"),
                terminalToken("Question", "?"),
                terminalToken("Plus", "+"),
                patternToken("Identifier",
                    "[a-zA-Z$](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?"),
                patternToken("StringContent", "[^\"\\\\]+"),
                patternToken("RegexContent", "[^/\\\\]+"),
                patternToken("CommentContent", "[^\r\n]+"),
                patternToken("Escape", Formats.ESCAPE_SEQUENCE),
                /* Whitespace and comments */
                prod("S", nt("Space", SYM_OPTIONAL)),
                prod("Comment", nt("StartComment"), nt("CommentContent"),
                     nt("EndOfLine")),
                prod("EOLX", nt("EndOfLine")),
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
                prod("Symbol", nt("Caret"), nt("Symbol", SYM_INLINE)),
                prod("Symbol", nt("Tilde"), nt("Symbol", SYM_INLINE)),
                /* Production definitions */
                prod("AlternativeList"),
                prod("AlternativeList", nt("Symbol"), nt("S", SYM_DISCARD),
                     nt("AlternativeList", SYM_INLINE)),
                prod("Alternative", nt("Percent", SYM_DISCARD),
                     nt("S", SYM_DISCARD)),
                prod("Alternative", nt("Symbol"), nt("S", SYM_DISCARD),
                     nt("AlternativeList", SYM_INLINE)),
                prod("ProductionContent", nt("Alternative")),
                prod("ProductionContent", nt("Alternative"),
                     nt("Bar", SYM_DISCARD), nt("LBS", SYM_DISCARD),
                     nt("ProductionContent", SYM_INLINE)),
                prod("Production", nt("Identifier"), nt("LBS", SYM_DISCARD),
                     nt("Equals", SYM_DISCARD), nt("LBS", SYM_DISCARD),
                     nt("ProductionContent"),
                     nt("EOLX", SYM_DISCARD)),
                /* File contents */
                prod("FileContentLine", nt("SEOLX", SYM_DISCARD)),
                prod("FileContentLine", nt("Production")),
                prod("FileContent",
                     nt("FileContentLine", SYM_INLINE | SYM_OPTIONAL |
                                           SYM_REPEAT)),
                /* Overall file structure */
                prod("File", nt("FileContent")),
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

        public static final UnionMapper<String> STRING_ELEMENT =
            new UnionMapper<String>();

        public static final UnionMapper<String> REGEX_ELEMENT =
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

        public static final Mapper<Pattern> REGEX =
            new CompositeMapper<String, Pattern>(REGEX_ELEMENT) {
                protected Pattern mapInner(Parser.ParseTree pt,
                                           List<String> children) {
                    StringBuilder sb = new StringBuilder();
                    for (String s : children) sb.append(s);
                    return Pattern.compile(sb.toString());
                }
            };

        public static final UnionMapper<Integer> SYMBOL_FLAG =
            new UnionMapper<Integer>();

        public static final UnionMapper<Grammar.Symbol> SYMBOL_CONTENT =
            new UnionMapper<Grammar.Symbol>();

        public static final Mapper<Grammar.Symbol> SYMBOL =
            new Mapper<Grammar.Symbol>() {
                public Grammar.Symbol map(Parser.ParseTree pt)
                        throws MappingException {
                    Grammar.Symbol base = null;
                    int flags = 0;
                    for (Parser.ParseTree child : pt.getChildren()) {
                        if (SYMBOL_FLAG.canMap(child)) {
                            flags |= SYMBOL_FLAG.map(child);
                            continue;
                        }
                        if (base != null)
                            throw new MappingException(
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

        public static final Mapper<List<Grammar.Symbol>> ALTERNATIVE =
            CompositeMapper.aggregate(SYMBOL);

        public static final Mapper<List<Grammar.Production>> PRODUCTIONS =
            new RecordMapper<List<Grammar.Production>>() {

                private final Mapper<String> NAME = add(PRODUCTION_NAME);
                private final Mapper<List<List<Grammar.Symbol>>> CONTENT =
                    add(CompositeMapper.aggregate(ALTERNATIVE));

                protected List<Grammar.Production> mapInner(Result res) {
                    List<Grammar.Production> ret =
                        new ArrayList<Grammar.Production>();
                    String name = res.get(NAME);
                    for (List<Grammar.Symbol> syms : res.get(CONTENT)) {
                        ret.add(new Grammar.Production(name, syms));
                    }
                    return ret;
                }

            };

        public static final Mapper<Grammar> GRAMMAR = new CompositeMapper<
                    List<Grammar.Production>, Grammar>(PRODUCTIONS) {
                protected Grammar mapInner(Parser.ParseTree pt,
                        List<List<Grammar.Production>> children) {
                    List<Grammar.Production> productions =
                        new ArrayList<Grammar.Production>();
                    for (List<Grammar.Production> ps : children) {
                        productions.addAll(ps);
                    }
                    return new Grammar(productions);
                }
            };

        public static final Mapper<Grammar> FILE = RecordMapper.wrap(GRAMMAR);

        public static final Mapper<Grammar> FILE_WRAPPER =
            RecordMapper.wrap(FILE);

        public static final Mapper<Parser.ParserGrammar> PARSER =
            new RecordMapper.WrapperMapper<Grammar,
                                           Parser.ParserGrammar>(FILE) {
                protected Parser.ParserGrammar process(Grammar value)
                        throws MappingException {
                    return new Parser.ParserGrammar(value);
                }
            };

        static {
            STRING_ELEMENT.add("StringContent", LeafMapper.string());
            STRING_ELEMENT.add("Escape", new LeafMapper<String>() {
                protected String mapInner(Parser.ParseTree pt)
                        throws MappingException {
                    try {
                        return Formats.parseEscapeSequence(pt.getContent(),
                                                           null, "\\\"");
                    } catch (IllegalArgumentException exc) {
                        throw new MappingException(exc.getMessage(), exc);
                    }
                }
            });

            REGEX_ELEMENT.add("RegexContent", LeafMapper.string());
            REGEX_ELEMENT.add("Escape", new LeafMapper<String>() {
                protected String mapInner(Parser.ParseTree pt)
                        throws MappingException {
                    String content = pt.getContent();
                    String parsed = Formats.tryParseEscapeSequence(content,
                                                                   "av", "/");
                    return (parsed != null) ? parsed : content;
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
                    public Grammar.Symbol map(Parser.ParseTree pt)
                            throws MappingException {
                        return Grammar.Symbol.terminal(STRING.map(pt));
                    }
                });
            SYMBOL_CONTENT.add("Regex",
                new Mapper<Grammar.Symbol>() {
                    public Grammar.Symbol map(Parser.ParseTree pt)
                            throws MappingException {
                        return Grammar.Symbol.pattern(REGEX.map(pt));
                    }
                });
        }

    }

    private Grammars() {}

    public static Parser.CompiledGrammar getMetaGrammar() {
        return MetaGrammar.COMPILED_INSTANCE;
    }

    public static Mapper<Grammar> getGrammarContentMapper() {
        return MapperHolder.GRAMMAR;
    }
    public static Mapper<Grammar> getGrammarFileMapper() {
        return MapperHolder.FILE_WRAPPER;
    }
    public static Mapper<Parser.ParserGrammar> getParserMapper() {
        return MapperHolder.PARSER;
    }

    private static Parser.ParserGrammar parseGrammarInner(Parser p)
            throws InvalidGrammarException, Parser.ParsingException {
        try {
            Parser.ParserGrammar ret = getParserMapper().map(p.parse());
            ret.validate();
            return ret;
        } catch (MappingException exc) {
            throw new AssertionError("The meta-grammar is buggy?!", exc);
        }
    }
    public static Parser.ParserGrammar parseGrammar(Reader input)
            throws InvalidGrammarException, Parser.ParsingException {
        return parseGrammarInner(getMetaGrammar().makeParser(input));
    }
    public static Parser.ParserGrammar parseGrammar(LineColumnReader input)
            throws InvalidGrammarException, Parser.ParsingException {
        return parseGrammarInner(getMetaGrammar().makeParser(input));
    }

}
