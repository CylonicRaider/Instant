#!/usr/bin/env python3
# -*- coding: ascii -*-

import sys, os, re

RAW_IDENT = r'[a-zA-Z_$][a-zA-Z0-9_$]*'
DOT_IDENT = RAW_IDENT + r'(\s*\.\s*' + RAW_IDENT + ')*'
KEYWORDS = ('abstract|assert|boolean|break|byte|case|catch|char|class|const|'
    'continue|default|do|double|else|enum|extends|final|finally|float|for|'
    'goto|if|implements|import|instanceof|int|interface|long|native|new|'
    'package|private|protected|public|return|short|static|strictfp|super|'
    'switch|synchronized|this|throw|throws|transient|try|void|volatile|'
    'while|'
    # While not technically keywords, those are equivalent for our purposes.
    'true|false|null')
REGEXES = {
    # Identifier. Not matching the "e" in float literals.
    # FIXME: Unicode support.
    'identifier': re.compile(r'(?<![a-zA-Z0-9_$])(?!%s)(?P<name>%s)' %
                             (KEYWORDS, DOT_IDENT)),
    # Interesting statement.
    'import': re.compile(r'(?<![a-zA-Z0-9_$])import\s+(static\s+)?'
                         r'(?P<name>%s(\s*\.\s*\*)?);' % DOT_IDENT),
    # Character or string. (The code is supposed to be syntactically valid.)
    'charstring': re.compile(r'''(?s)'([^']|\.)+'|"([^"]|\.)*"'''),
    # Comments.
    'linecomment': re.compile(r'(?m)//.*?$'),
    'blockcomment': re.compile(r'(?s)/\*.*?\*/'),
    }

def match_tokens(data, regexes):
    """
    Regex race algorithm, as seen in the Instant frontend
    """
    pos, hits = 0, dict((k, None) for k in regexes)
    while pos != len(data):
        # Update regex matches.
        for k, v in tuple(hits.items()):
            if v is None or v.start() < pos:
                m = REGEXES[k].search(data, pos)
                if m:
                    hits[k] = m
                else:
                    del hits[k]
        # Abort if no more matches.
        if not hits: break
        # Find first match.
        firstname = min(hits, key=lambda k: hits[k].start())
        first = hits[firstname]
        # Emit preceding data and the match.
        if first.start() != pos: yield data[pos:first.start()]
        yield (firstname, first)
        # Prepare for next round.
        pos = first.end()
    # Emit remainder.
    if pos != len(data):
        yield data[pos:]

def tokenize(data):
    """
    Partition a Java source file into a list of interesting and
    non-interesting bits along with indexes of the formers
    """
    def normalize_tok_name(tok):
        return re.sub(r'\s+', '', tok.group('name'))
    lineno, ret, imports, idents = 1, [], [], []
    for ent in match_tokens(data, REGEXES):
        if isinstance(ent, str):
            ret.append(ent)
            # FIXME: Assuming no CR-only newlines...
            lineno += ent.count('\n')
            continue
        name, tok = ent
        if name == 'identifier':
            # Indirect addressing to ease sorting imports below.
            ret.append(('ident', len(idents), lineno))
            idents.append((tok.group(), normalize_tok_name(tok)))
        elif name == 'import':
            ret.append(('import', len(imports), lineno))
            imports.append((tok.group(), normalize_tok_name(tok)))
        else:
            ret.append(tok.group())
        lineno += ent.count('\n')
    return {None: ret, 'import': imports, 'ident': idents}

def main():
    """
    Main function
    """
    pass

if __name__ == '__main__': main()
