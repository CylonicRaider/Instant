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
    'while')
REGEXES = {
    # Identifier. Not matching the "e" in float literals.
    # FIXME: Unicode support.
    'identifier': re.compile(r'(?!%s)(?<![a-zA-Z0-9_$])%s' %
                             (KEYWORDS, DOT_IDENT)),
    # Interesting statement.
    'import': re.compile(r'(?<![a-zA-Z0-9_$])import\s+(static\s+)?'
                         r'(?P<name>%s);' % DOT_IDENT),
    # Character or string. (The code is supposed to be syntactically valid.)
    'charstring': re.compile(r'''(?s)'([^']|\.)+'|"([^"]|\.)*"'''),
    # Comments.
    'linecomment': re.compile(r'(?m)//.*?$'),
    'blockcomment': re.compile(r'(?s)/\*.*?\*/'),
    # Line separator.
    'newline': re.compile(r'\n')
    }

def regex_race(data, regexes):
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
        first = min(hits.values(), key=lambda m: m.start())
        # Emit preceding data and the match.
        if first.start() != pos: yield data[pos:first.start()]
        yield first
        # Prepare for next round.
        pos = first.end()
    # Emit remainder.
    if pos != len(data):
        yield data[pos:]

def main():
    """
    Main function
    """
    pass

if __name__ == '__main__': main()
