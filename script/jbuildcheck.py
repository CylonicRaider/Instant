#!/usr/bin/env python3
# -*- coding: ascii -*-

"""
A script for locating Java source files in need of a recompile.
"""

import sys, os, re

COMMENT_RE = re.compile(r'^\s*(#.*)?$')
MAP_LINE_RE = re.compile(r'^([^\s:#]+):((?:\s*[^\s:#]+)*)\s*$')

def path_matches(dirname, filename):
    "Check whether filename is inside dirname (..-s notwithstanding)"
    ld = len(dirname)
    return (filename.startswith(dirname) and
            (dirname.endswith(os.path.sep) or
             filename[ld:ld + 1] in ('', os.path.sep)))

def parse_map(fp):
    "Parse a dependency map as created by importlint.py"
    ret = {}
    for n, line in enumerate(fp, 1):
        line = line.strip()
        if COMMENT_RE.match(line): continue
        m = MAP_LINE_RE.match(line)
        if not m: raise ValueError('Invalid input line: %r' % (line,))
        filename, rawdeps = m.group(1, 2)
        ret.setdefault(filename, set()).update(rawdeps.split())
    return ret

def main():
    # Parse command line.
    mapfile, cleanup, filters = None, False, []
    try:
        it, args_only = iter(sys.argv[1:]), False
        for opt in it:
            if args_only or not opt.startswith('-'):
                filters.append(opt)
            elif opt == '--':
                args_only = True
            elif opt == '--help':
                sys.stderr.write('USAGE: %s [--help] [--[no-]cleanup] '
                        '[--map MAPFILE] [DIR [DIR ...]]\n'
                    'Locate Java source files needing a recompile and print '
                        'their paths to standard output.\n'
                    '--help   : Display help.\n'
                    '--cleanup: Also delete stale class files.\n'
                    '--map    : A file describing the dependencies among '
                        'Java source files. Only files listed in the map are '
                        'inspected. Use "importlint.py --deps" to generate '
                        'this. If not specified, standard input is read.\n'
                    'DIR      : If not given, report all files; otherwise, '
                        'report files located in DIR.\n' %
                    sys.argv[0])
                raise SystemExit
            elif opt == '--cleanup':
                cleanup = True
            elif opt == '--no-cleanup':
                cleanup = False
            elif opt == '--map':
                mapfile = next(it)
            else:
                raise SystemExit('Unrecognized option %r!' % opt)
    except StopIteration:
        raise SystemExit('Missing required value for option %r!' % opt)
    except ValueError:
        raise SystemExit('Invalid value for option %r!' % opt)
    # Gather dependency map.
    if mapfile is None:
        depmap = parse_map(sys.stdin)
    else:
        with open(mapfile) as f:
            depmap = parse_map(f)
    # Apply the filters.
    if not filters:
        files = list(depmap)
    else:
        files = [f for f in depmap
                 if any(path_matches(d, f) for d in filters)]
    # The rest is NYI.
    raise SystemExit('Error: Not yet implemented')

if __name__ == '__main__': main()
