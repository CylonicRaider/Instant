package net.instant.util.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.instant.api.parser.CompiledGrammar;
import net.instant.api.parser.Grammar;
import net.instant.api.parser.InvalidGrammarException;
import net.instant.api.parser.Mapper;
import net.instant.api.parser.Mappers;
import net.instant.api.parser.MappingException;
import net.instant.api.parser.Parser;
import net.instant.api.parser.ParsingException;
import net.instant.api.parser.RecordMapper;
import net.instant.api.parser.TransformMapper;
import net.instant.api.parser.UnionMapper;
import net.instant.util.Formats;
import net.instant.util.LineColumnReader;

public final class Grammars {

    private static class MetaGrammar extends GrammarImpl {

        public static final MetaGrammar INSTANCE;
        public static final CompiledGrammar COMPILED_INSTANCE;

        static {
            INSTANCE = new MetaGrammar();
            try {
                COMPILED_INSTANCE = ParserImpl.compile(INSTANCE);
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
            return new ProductionImpl(name, symbols);
        }

    }

    private static class MapperHolder {

        public static final UnionMapper<String> STRING_ELEMENT =
            new UnionMapper<String>();

        public static final UnionMapper<String> REGEX_ELEMENT =
            new UnionMapper<String>();

        public static final Mapper<String> STRING =
            new RecordMapper<String>() {
                protected String mapInner(Provider p)
                        throws MappingException {
                    StringBuilder sb = new StringBuilder();
                    while (p.hasNext()) sb.append(p.mapNext(STRING_ELEMENT));
                    return sb.toString();
                }
            };

        public static final Mapper<Pattern> REGEX =
            new RecordMapper<Pattern>() {
                protected Pattern mapInner(Provider p)
                        throws MappingException {
                    StringBuilder sb = new StringBuilder();
                    while (p.hasNext()) sb.append(p.mapNext(REGEX_ELEMENT));
                    return Pattern.compile(sb.toString());
                }
            };

        public static final UnionMapper<Integer> SYMBOL_FLAG =
            new UnionMapper<Integer>();

        public static final UnionMapper<Grammar.Symbol> SYMBOL_CONTENT =
            new UnionMapper<Grammar.Symbol>();

        public static final Mapper<Grammar.Symbol> SYMBOL =
            new RecordMapper<Grammar.Symbol>() {
                protected Grammar.Symbol mapInner(Provider p)
                        throws MappingException {
                    Grammar.Symbol base = null;
                    int flags = 0;
                    for (Parser.ParseTree child : p) {
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
            Mappers.content();

        public static final Mapper<List<Grammar.Symbol>> ALTERNATIVE =
            Mappers.aggregate(SYMBOL);

        public static final Mapper<List<List<Grammar.Symbol>>> ALTERNATIVES =
            Mappers.aggregate(ALTERNATIVE);

        public static final Mapper<List<Grammar.Production>> PRODUCTIONS =
            new RecordMapper<List<Grammar.Production>>() {
                protected List<Grammar.Production> mapInner(Provider p)
                        throws MappingException {
                    String name = p.mapNext(PRODUCTION_NAME);
                    List<Grammar.Production> ret =
                        new ArrayList<Grammar.Production>();
                    for (List<Grammar.Symbol> syms :
                         p.mapNext(ALTERNATIVES)) {
                        ret.add(new GrammarImpl.ProductionImpl(name, syms));
                    }
                    return ret;
                }
            };

        public static final Mapper<Grammar> GRAMMAR =
            new TransformMapper<List<Grammar.Production>, Grammar>(
                    Mappers.join(PRODUCTIONS)) {
                protected Grammar transform(List<Grammar.Production> prods)
                        throws MappingException {
                    GrammarImpl ret = new GrammarImpl(prods);
                    try {
                        ret.validate();
                    } catch (InvalidGrammarException exc) {
                        throw new MappingException(exc);
                    }
                    return ret;
                }
            };

        public static final Mapper<Grammar> FILE = Mappers.unwrap(GRAMMAR);

        public static final Mapper<Grammar> FILE_WRAPPER =
            Mappers.unwrap(FILE);

        static {
            STRING_ELEMENT.add("StringContent", Mappers.content());
            STRING_ELEMENT.add("Escape", new TransformMapper<String,
                    String>(Mappers.content()) {
                protected String transform(String content)
                        throws MappingException {
                    try {
                        return Formats.parseEscapeSequence(content, null,
                                                           "\\\"");
                    } catch (IllegalArgumentException exc) {
                        throw new MappingException(exc.getMessage(), exc);
                    }
                }
            });

            REGEX_ELEMENT.add("RegexContent", Mappers.content());
            REGEX_ELEMENT.add("Escape", new TransformMapper<String,
                    String>(Mappers.content()) {
                protected String transform(String content)
                        throws MappingException {
                    String parsed = Formats.tryParseEscapeSequence(
                        content, "abtnvfr", "");
                    return (parsed != null) ? parsed : content;
                }
            });

            SYMBOL_FLAG.add("Caret",
                            Mappers.constant(Grammar.Symbol.SYM_INLINE));
            SYMBOL_FLAG.add("Tilde",
                            Mappers.constant(Grammar.Symbol.SYM_DISCARD));
            SYMBOL_FLAG.add("Question",
                            Mappers.constant(Grammar.Symbol.SYM_OPTIONAL));
            SYMBOL_FLAG.add("Plus",
                            Mappers.constant(Grammar.Symbol.SYM_REPEAT));

            SYMBOL_CONTENT.add("Identifier", new TransformMapper<String,
                    Grammar.Symbol>(Mappers.content()) {
                protected Grammar.Symbol transform(String content) {
                    return new GrammarImpl.Nonterminal(content, 0);
                }
            });
            SYMBOL_CONTENT.add("String", new TransformMapper<String,
                    Grammar.Symbol>(STRING) {
                protected Grammar.Symbol transform(String value) {
                    return new GrammarImpl.FixedTerminal(value, 0);
                }
            });
            SYMBOL_CONTENT.add("Regex", new TransformMapper<Pattern,
                    Grammar.Symbol>(REGEX) {
                protected Grammar.Symbol transform(Pattern pattern) {
                    return new GrammarImpl.Terminal(pattern, 0);
                }
            });
        }

    }

    private Grammars() {}

    public static CompiledGrammar getMetaGrammar() {
        return MetaGrammar.COMPILED_INSTANCE;
    }

    public static Mapper<Grammar> getGrammarContentMapper() {
        return MapperHolder.GRAMMAR;
    }
    public static Mapper<Grammar> getGrammarFileMapper() {
        return MapperHolder.FILE_WRAPPER;
    }

    private static Grammar parseGrammarInner(Parser p)
            throws ParsingException {
        try {
            return getGrammarFileMapper().map(p.parse());
        } catch (MappingException exc) {
            throw new ParsingException(null, exc);
        } finally {
            try {
                p.close();
            } catch (IOException exc) {
                throw new ParsingException(
                    p.getTokenSource().getCurrentLocation(),
                    "Exception while closing parser: " + exc.getMessage(),
                    exc);
            }
        }
    }
    public static Grammar parseGrammar(LineColumnReader input)
            throws ParsingException {
        return parseGrammarInner(getMetaGrammar().createParser(
            new Lexer(input), false));
    }
    public static Grammar parseGrammar(Reader input)
            throws ParsingException {
        return parseGrammar(new LineColumnReader(input));
    }

    public static String formatWithSymbolFlags(String base, int flags) {
        StringBuilder sb = new StringBuilder();
        if ((flags & Grammar.Symbol.SYM_INLINE  ) != 0) sb.append('^');
        if ((flags & Grammar.Symbol.SYM_DISCARD ) != 0) sb.append('~');
        sb.append(base);
        if ((flags & Grammar.Symbol.SYM_OPTIONAL) != 0) sb.append('?');
        if ((flags & Grammar.Symbol.SYM_REPEAT  ) != 0) sb.append('+');
        flags &= ~Grammar.Symbol.SYM_ALL;
        if (flags != 0)
            sb.append("[0x").append(Integer.toHexString(flags)).append("]");
        return sb.toString();
    }

}
