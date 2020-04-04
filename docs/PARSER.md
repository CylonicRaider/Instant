# Instant parser module documentation

This file documents the textual grammar representation format used by
Instant's implementation of the parser module and the limitations on the
types of grammars is supports.

## Introduction

A *parser* processes a stream of input characters (which will henceforth be
treated as a single string), attempts to match it against a *grammar* that
defines a *language* (_i.e._ a set of strings) of allowed inputs, and produces
either a *parse tree* (if parsing succeeds) or an error. Parse trees consist
of *nodes*, which have a *name*, and either (zero or more) *child nodes* or a
single corresponding *token*.

## Grammar format

A grammar consists of *productions*, which, in turn, consist of *symbols*;
some of the productions are *token definitions*; in addition, a grammar has a
special *start symbol*.

*Comments* are introduced by number signs (`#`) and last until line breaks (or
the end-of-file). Empty lines are ignored.

### Productions

A production has a *name* and a set of *alternatives*, which in turn are
sequences of symbols.

Production names must be valid *identifiers*, _i.e._ match the regular
expression `[a-zA-Z$_-][A-Za-z0-9$_-]*` (a nonempty sequence of letters,
digits, underscores (`_`), hyphens (`-`), or dollar signs (`$`), where the
first character may not be a digit). Identifiers are case-sensitive (`test`
and `tEsT` are different idenfiers); identifiers starting with dollar signs
are reserved for special uses.

Productions are defined on individual ("virtual") lines using the following
syntax:

    NAME = SYM1 SYM2 ... | SYM3 SYM4 ... | ...

That is, the name of the production (as a bare word) folowed by an equals sign
(`=`) and a list of alternatives delimited by vertical bar characters (`|`),
with the alternatives consisting of consecutive symbol definitions (with
symbols being described [below](#symbols)). An empty alternative is denoted by
a single percent sign (`%`). Optional whitespace may be included between any
of the components; in particular, including whitespace between adjacent
symbols is *highly* encouraged. Lines breaks may appear around the equals sign
and *after* vertical bar characters, splitting the production definition onto
multiple "physical" lines. A line break after the last alternative is
mandatory (an end-of-file is *not* permissible).

A production matches *any* of the strings matched by any of its alternatives,
which, in turn, match the *concatenations* of the strings matched by their
symbols. For example, the production `foo = "A" "B" "C" | "X" "Y" "Z"` matches
the strings `ABC` or `XYZ`.

### Symbols

A symbol belongs to a particular type, has a textual content, and has a set of
flags. The type and the content are expressed using special syntax:

- A *nonterminal* symbol is expressed by a bare word that must be a valid
  identifier and names a production. The symbol matches those strings that are
  matched by the corresponding production. For example, the string `test`
  denotes a nonterminal symbol that refers to productions called `test`, and
  consequently matches anything that the production `test` matches.

- A *fixed terminal* symbol is expressed by a sequence of characters enclosed
  in double quotes (`"`). Backslashes (`\`) escape the characters following
  them, allowing literal double quotes and backslashes to be included; in
  addition, escape sequences are provides for certain characters (such as `\r`
  and `\n` for the carriage return and the line feed characters). For example,
  the symbol `"test"` matches (only) the string `test`.

- A *pattern terminal* symbol is expressed by a regular expression pattern
  enclosed by slashes (`/`). Backslashes can, again, be used to escape
  following characters, including most notably slashes; unrecognized escape
  sequences are passed on to the regular expression compiler. Certain regular
  expression constructs require escaping of the leading backslash from the
  grammar parser (_e.g._, `\b` is a backspace character while `\\b` is a word
  boundary). For example, the symbol `/tes+t/` matches the strings `test`,
  `tesst`, `tessst`, and so on.

Flags are expressed by prepending or appending a "modifier" character to the
above representations of symbols. Multiple flags can be specified with no
regard for order (as long as they are at the correct positions w.r.t. the
"base" symbol). There may be *no* whitespace between a "base" symbol and its
flags. The following flags are defined:

- *Inline*: leading caret (`^`). For nonterminals, specifies that no
  sub-parse-tree is to be generated for the symbol; for example, if the
  production `foo = "A" "B"` is given, the production `bar = "X" ^foo "Y"`
  is equivalent to `bar = "X" "A" "B" "Y"`. For terminals, this flag is used
  in token definitions; for example, the production `number = ^/[1-9][0-9]*/`
  defines a class of tokens called `number` whose contents are decimal
  numbers.

- *Discard*: leading tilde (`~`). Specifies that no sub-parse-tree is to be
  generated for the symbol whatsoever; for example, the production
  `foo = "A" ~"B" "C"` matches (only) the string `ABC` but produces a parse
  tree containing subtrees only for the `"A"` and the `"C"`. Useful, in
  particular, for non-significant whitespace.

- *Optional*: trailing question mark (`?`). Specifies that matching may skip
  past the symbol; for example, the production `foo = "X" "Y"?` matches the
  strings `X` or `XY`. Might be useful together with *Discard*.

- *Repeated*: trailing plus (`+`). Specifies that matching may match the
  symbol multiple times; for example, the production `foo = "te" "s"+ "t"`
  matches the same strings as the symbol `/tes+t/`. May be combined with
  *Optional* to obtain any amount of repetition, including none.

If both *Inline* and *Discard* are specified for a symbol, the latter "wins"
(the inlined contents are discarded, or there is nothing to inline).

### Token definitions

Certain (rather restricted) productions define classes of *tokens*, each of
which has a *name*, a textual *content* and a *location* denoting where in the
input stream it appeared. Tokens form the leaves of parse trees (together with
"regular" that produce no sub-trees for other reasons). Of the above
attributes, token definitions provide names and sets of possible contents.

A token definition is a production that contains exactly one alternative and
exactly one terminal symbol that only has the *Inline* flag set. (As applying
the *Inline* flag to a terminal symbol is meaningless _a priori_ (a single
token has no parse sub-parse-trees to inline), this does not produce any
limitation.) For example, the production
`ident = ^/[a-zA-Z$_-][A-Za-z0-9$_-]*/` defines a class of tokens that are
called `ident` and whose contents are valid idenfiers.

Aside from their token-naming properties, token definitions act just like
regular productions (which happen to contain only one terminal symbol each).

### Start symbol

The start symbol of a grammar defines what language the grammar as a whole
recognizes and acts as an entry point into the productions of the grammar.

No special syntax is provided for defining the start symbol; instead, it is
the fixed nonterminal symbol `$start`. Productions for it may (and should!) be
defined. The start symbol may also act as the name of a token definition (in
that case, the grammar matches only that token).

### Examples

The following (rather minimal) grammar matches merely the string `test`:

    $start = "test"

The following grammar matches sequences of balanced parentheses (including the
empty string):

    $start = % | "(" $start ")" $start

The following grammar matches simple arithmetical expressions:

    # Token definitions
    Plus = ^"+"
    Minus = ^"-"
    Times = ^"*"
    Divide = ^"/"
    OpeningBracket = ^"("
    ClosingBracket = ^")"
    Number = ^/[1-9][0-9]*/

    # The grammar proper
    $start = ^Sum
    Sum = Product | ^Sum Plus Product | ^Sum Minus Product
    Product = Atom | ^Product Times Atom | ^Product Divide Atom
    Atom = ~OpeningBracket ^Sum ~ClosingBracket | Number

## Implementation limitations

— *to be continued* —
