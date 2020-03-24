package net.instant.util.parser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.instant.util.Formats;
import net.instant.util.LineColumnReader;

public final class Grammars {

    private static class MetaGrammar extends Grammar {

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
                pattern("EndOfLine", "\r?\n|\r"),
                pattern("Space", "[ \t\u000b\f]+"),
                terminal("Equals", "="),
                terminal("Bar", "|"),
                terminal("Quote", "\""),
                terminal("Slash", "/"),
                terminal("Percent", "%"),
                pattern("StartComment", "[#;]"),
                terminal("Caret", "^"),
                terminal("Tilde", "~"),
                terminal("Question", "?"),
                terminal("Plus", "+"),
                pattern("Identifier",
                    "[a-zA-Z$](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?"),
                pattern("StringContent", "[^\"\\\\]+"),
                pattern("RegexContent", "[^/\\\\]+"),
                pattern("CommentContent", "[^\r\n]+"),
                pattern("Escape", Formats.ESCAPE_SEQUENCE),
                /* Whitespace and comments */
                prod("S", nt("Space", Symbol.SYM_OPTIONAL)),
                prod("Comment", nt("StartComment"), nt("CommentContent"),
                     nt("EndOfLine")),
                prod("EOLX", nt("EndOfLine")),
                prod("EOLX", nt("Comment")),
                prod("SEOLX", nt("S"), nt("EOLX")),
                prod("LBS", nt("S")),
                prod("LBS", nt("S"), nt("EOLX"),
                     nt("LBS", Symbol.SYM_INLINE)),
                /* Symbol definitions */
                prod("SymbolTrailer"),
                prod("SymbolTrailer",
                     nt("Question"),
                     nt("SymbolTrailer", Symbol.SYM_INLINE)),
                prod("SymbolTrailer",
                     nt("Plus"),
                     nt("SymbolTrailer", Symbol.SYM_INLINE)),
                prod("String"),
                prod("String",
                     nt("StringContent"),
                     nt("String", Symbol.SYM_INLINE)),
                prod("String",
                     nt("Escape"),
                     nt("String", Symbol.SYM_INLINE)),
                prod("Regex"),
                prod("Regex",
                     nt("RegexContent"),
                     nt("Regex", Symbol.SYM_INLINE)),
                prod("Regex",
                     nt("Escape"),
                     nt("Regex", Symbol.SYM_INLINE)),
                prod("Symbol",
                     nt("Identifier"),
                     nt("SymbolTrailer", Symbol.SYM_INLINE)),
                prod("Symbol",
                     nt("Quote", Symbol.SYM_DISCARD),
                     nt("String"),
                     nt("Quote", Symbol.SYM_DISCARD),
                     nt("SymbolTrailer", Symbol.SYM_INLINE)),
                prod("Symbol",
                     nt("Slash", Symbol.SYM_DISCARD),
                     nt("Regex"),
                     nt("Slash", Symbol.SYM_DISCARD),
                     nt("SymbolTrailer", Symbol.SYM_INLINE)),
                prod("Symbol", nt("Caret"), nt("Symbol", Symbol.SYM_INLINE)),
                prod("Symbol", nt("Tilde"), nt("Symbol", Symbol.SYM_INLINE)),
                /* Production definitions */
                prod("AlternativeList"),
                prod("AlternativeList",
                     nt("Symbol"),
                     nt("S", Symbol.SYM_DISCARD),
                     nt("AlternativeList", Symbol.SYM_INLINE)),
                prod("Alternative",
                     nt("Percent", Symbol.SYM_DISCARD),
                     nt("S", Symbol.SYM_DISCARD)),
                prod("Alternative",
                     nt("Symbol"),
                     nt("S", Symbol.SYM_DISCARD),
                     nt("AlternativeList", Symbol.SYM_INLINE)),
                prod("ProductionContent", nt("Alternative")),
                prod("ProductionContent",
                     nt("Alternative"),
                     nt("Bar", Symbol.SYM_DISCARD),
                     nt("LBS", Symbol.SYM_DISCARD),
                     nt("ProductionContent", Symbol.SYM_INLINE)),
                prod("Production", nt("Identifier"),
                     nt("LBS", Symbol.SYM_DISCARD),
                     nt("Equals", Symbol.SYM_DISCARD),
                     nt("LBS", Symbol.SYM_DISCARD),
                     nt("ProductionContent"),
                     nt("EOLX", Symbol.SYM_DISCARD)),
                /* File contents */
                prod("FileContentLine", nt("SEOLX", Symbol.SYM_DISCARD)),
                prod("FileContentLine", nt("Production")),
                prod("FileContent",
                     nt("FileContentLine", Symbol.SYM_INLINE |
                                           Symbol.SYM_OPTIONAL |
                                           Symbol.SYM_REPEAT)),
                /* Overall file structure */
                prod("File", nt("FileContent")),
                prod(START_SYMBOL.getReference(), nt("File"))
            );
        }

        private static Production terminal(String name, String content) {
            return prod(name, new FixedTerminal(content, Symbol.SYM_INLINE));
        }
        private static Production pattern(String name, Pattern content) {
            return prod(name, new Terminal(content, Symbol.SYM_INLINE));
        }
        private static Production pattern(String name, String content) {
            return pattern(name, Pattern.compile(content));
        }

        private static Symbol nt(String reference) {
            return new Nonterminal(reference, 0);
        }
        private static Symbol nt(String reference, int flags) {
            return new Nonterminal(reference, flags);
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
                protected String mapInner(ParseTree pt,
                                          List<String> children) {
                    StringBuilder sb = new StringBuilder();
                    for (String s : children) sb.append(s);
                    return sb.toString();
                }
            };

        public static final Mapper<Pattern> REGEX =
            new CompositeMapper<String, Pattern>(REGEX_ELEMENT) {
                protected Pattern mapInner(ParseTree pt,
                                           List<String> children) {
                    StringBuilder sb = new StringBuilder();
                    for (String s : children) sb.append(s);
                    return Pattern.compile(sb.toString());
                }
            };

        public static final UnionMapper<Integer> SYMBOL_FLAG =
            new UnionMapper<Integer>();

        public static final UnionMapper<Symbol> SYMBOL_CONTENT =
            new UnionMapper<Symbol>();

        public static final Mapper<Symbol> SYMBOL = new Mapper<Symbol>() {
            public Symbol map(ParseTree pt) throws MappingException {
                Symbol base = null;
                int flags = 0;
                for (ParseTree child : pt.getChildren()) {
                    if (SYMBOL_FLAG.canMap(child)) {
                        flags |= SYMBOL_FLAG.map(child);
                        continue;
                    }
                    if (base != null)
                        throw new MappingException(
                            "Incorrect redundant symbol content");
                    base = SYMBOL_CONTENT.map(child);
                }
                return base.withFlags(flags);
            }
        };

        public static final Mapper<String> PRODUCTION_NAME =
            LeafMapper.string();

        public static final Mapper<List<Symbol>> ALTERNATIVE =
            CompositeMapper.aggregate(SYMBOL);

        public static final Mapper<List<Production>> PRODUCTIONS =
            new RecordMapper<List<Production>>() {

                private final Mapper<String> NAME = add(PRODUCTION_NAME);
                private final Mapper<List<List<Symbol>>> CONTENT =
                    add(CompositeMapper.aggregate(ALTERNATIVE));

                protected List<Production> mapInner(Result res) {
                    List<Production> ret =
                        new ArrayList<Production>();
                    String name = res.get(NAME);
                    for (List<Symbol> syms : res.get(CONTENT)) {
                        ret.add(new Production(name, syms));
                    }
                    return ret;
                }

            };

        public static final Mapper<Grammar> GRAMMAR = new CompositeMapper<
                    List<Production>, Grammar>(PRODUCTIONS) {
                protected Grammar mapInner(ParseTree pt,
                        List<List<Production>> children) {
                    List<Production> productions =
                        new ArrayList<Production>();
                    for (List<Production> ps : children) {
                        productions.addAll(ps);
                    }
                    return new Grammar(productions);
                }
            };

        public static final Mapper<Grammar> FILE = RecordMapper.wrap(GRAMMAR);

        public static final Mapper<Grammar> FILE_WRAPPER =
            RecordMapper.wrap(FILE);

        static {
            STRING_ELEMENT.add("StringContent", LeafMapper.string());
            STRING_ELEMENT.add("Escape", new LeafMapper<String>() {
                protected String mapInner(ParseTree pt)
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
                protected String mapInner(ParseTree pt)
                        throws MappingException {
                    String content = pt.getContent();
                    String parsed = Formats.tryParseEscapeSequence(content,
                        "abtnvfr", "");
                    return (parsed != null) ? parsed : content;
                }
            });

            SYMBOL_FLAG.add("Caret",
                            LeafMapper.constant(Symbol.SYM_INLINE));
            SYMBOL_FLAG.add("Tilde",
                            LeafMapper.constant(Symbol.SYM_DISCARD));
            SYMBOL_FLAG.add("Question",
                            LeafMapper.constant(Symbol.SYM_OPTIONAL));
            SYMBOL_FLAG.add("Plus",
                            LeafMapper.constant(Symbol.SYM_REPEAT));

            SYMBOL_CONTENT.add("Identifier",
                new LeafMapper<Symbol>() {
                    protected Symbol mapInner(ParseTree pt) {
                        return new Nonterminal(pt.getContent(), 0);
                    }
                });
            SYMBOL_CONTENT.add("String",
                new Mapper<Symbol>() {
                    public Symbol map(ParseTree pt)
                            throws MappingException {
                        return new FixedTerminal(STRING.map(pt), 0);
                    }
                });
            SYMBOL_CONTENT.add("Regex",
                new Mapper<Symbol>() {
                    public Symbol map(ParseTree pt)
                            throws MappingException {
                        return new Terminal(REGEX.map(pt), 0);
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

    private static Grammar parseGrammarInner(Parser p)
            throws InvalidGrammarException, Parser.ParsingException {
        try {
            Grammar ret = getGrammarFileMapper().map(p.parse());
            ret.validate();
            return ret;
        } catch (MappingException exc) {
            throw new AssertionError("The meta-grammar is buggy?!", exc);
        }
    }
    public static Grammar parseGrammar(Reader input)
            throws InvalidGrammarException, Parser.ParsingException {
        return parseGrammarInner(getMetaGrammar().makeParser(
            getMetaGrammar().makeLexer(input)));
    }
    public static Grammar parseGrammar(LineColumnReader input)
            throws InvalidGrammarException, Parser.ParsingException {
        return parseGrammarInner(getMetaGrammar().makeParser(
            getMetaGrammar().makeLexer(input)));
    }

    public static String formatWithSymbolFlags(String base, int flags) {
        StringBuilder sb = new StringBuilder();
        if ((flags & Symbol.SYM_INLINE  ) != 0) sb.append('^');
        if ((flags & Symbol.SYM_DISCARD ) != 0) sb.append('~');
        sb.append(base);
        if ((flags & Symbol.SYM_OPTIONAL) != 0) sb.append('?');
        if ((flags & Symbol.SYM_REPEAT  ) != 0) sb.append('+');
        flags &= ~Symbol.SYM_ALL;
        if (flags != 0)
            sb.append("[0x").append(Integer.toHexString(flags)).append("]");
        return sb.toString();
    }

}
