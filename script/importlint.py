#!/usr/bin/env python3
# -*- coding: ascii -*-

import sys, os, re

RAW_IDENT = r'[a-zA-Z_$][a-zA-Z0-9_$]*'
REGEXES = {
    # Identifier. Not matching the "e" in float literals.
    # FIXME: Unicode support.
    'identifier': re.compile(r'(?!<[a-zA-Z0-9_$])' + RAW_IDENT),
    # Interesting statement.
    'import': re.compile(r'(?!<[a-zA-Z0-9_$])import\s+(static\s+)?'
        r'(?P<name>%(i)s\s*(\.\s*%(i)s\s*)*);' % {'i': RAW_IDENT}),
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
        for k, v in hits.items():
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
        # Emit it.
        yield first
        # Prepare for next round.
        pos = first.end()

def main():
    """
    Main function
    """
    pass

if __name__ == '__main__': main()
