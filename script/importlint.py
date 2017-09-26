#!/usr/bin/env python3
# -*- coding: ascii -*-

import sys, os, re
import functools

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
    # Package declaration.
    'package': re.compile(r'(?<![a-zA-Z0-9_$])package\s+(?P<name>%s)\s*;' %
                          DOT_IDENT),
    # Import. Attempts to capture surrounding whitespace (such as the line
    # terminator), but not too much, for easier pruning.
    'import': re.compile(r'(?m)(^[^\n\S]*)?(?<![a-zA-Z0-9_$])import\s+'
                         r'((?P<static>static)\s+)?(?P<name>%s)\s*;'
                         r'[^\n\S]*\n?' % DOT_IDENT),
    # Character or string. (The code is supposed to be syntactically valid.)
    'charstring': re.compile(r'''(?s)'([^\\']|\\.)+'|"([^\\"]|\\.)*"'''),
    # Comments.
    'linecomment': re.compile(r'(?m)//.*?$'),
    'blockcomment': re.compile(r'(?s)/\*.*?\*/'),
    }

def normalize_name(name):
    "Normalize an identifier to have no whitespace"
    return re.sub(r'\s+', '', name)
def leading_name(name):
    "Extract the leading portion of an identifier"
    return name.partition('.')[0]
def trailing_name(name):
    "Extract the trailing portion of an identifier"
    return name.rpartition('.')[2]

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
    lineno, ret, imports, idents, package = 1, [], [], [], None
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
            idents.append((tok, normalize_name(tok.group('name'))))
        elif name == 'import':
            ret.append(('import', len(imports), lineno))
            imports.append((tok, normalize_name(tok.group('name'))))
        elif name == 'package':
            ret.append(tok.group())
            package = normalize_name(tok.group('name'))
        else:
            ret.append(tok.group())
        lineno += ent.count('\n')
    return {None: ret, 'import': imports, 'ident': idents,
            'package': package}

def importlint(filename, warn=True, sort=False, prune=False,
               empty_lines=False):
    """
    Check a Java source file for superfluous imports and optionally sort them
    """
    def sortkey(ent):
        "Sorting key for imports"
        return [ent[0].group('static') or ''] + ent[1].split('.')
    mode, writeback = ('r+' if sort or prune else 'r'), False
    with open(filename, mode) as f:
        # Gather data.
        info = tokenize(f.read())
        parts, imports, idents = info[None], info['import'], info['ident']
        package = info['package']
        # Enumerate used and imported names.
        used = set(leading_name(n[1]) for n in idents)
        imported, redundant = set(), set()
        for n in imports:
            pref, sep, bn = n[1].rpartition('.')
            imported.add(bn)
            if sep and pref in (package, 'java.lang'):
                redundant.add(n[1])
        excess = imported.difference(used)
        remove = excess.union(trailing_name(n) for n in redundant)
        # Reomove excess ones.
        if prune:
            seen = set()
            for n, ent in enumerate(imports):
                if trailing_name(ent[1]) in remove:
                    imports[n] = None
                elif ent[1] in seen:
                    imports[n] = None
                else:
                    seen.add(ent[1])
            if None in imports:
                sys.stderr.write('%s: note: removed superfluous imports\n' %
                                 filename)
                writeback = True
        # Sort them.
        if sort:
            oi = list(imports)
            if None in imports:
                indices = [n for n, i in enumerate(imports) if i]
                indices.sort(key=lambda n: sortkey(imports[n]))
                it = iter(indices)
                for n, i in enumerate(imports):
                    if i: imports[n] = oi[next(it)]
            else:
                imports.sort(key=sortkey)
            if imports != oi:
                sys.stderr.write('%s: note: rearranged imports\n' % filename)
                writeback = True
        # Overwrite file.
        if writeback:
            f.seek(0)
            nlstate, do_nlprune = 0, False
            for ent in parts:
                if isinstance(ent, tuple):
                    ref = info[ent[0]][ent[1]]
                    if ref is None:
                        do_nlprune = True
                        continue
                    tw = ref[0].group()
                else:
                    tw = ent
                if empty_lines:
                    if nlstate > 1 and tw.startswith('\n') and do_nlprune:
                        tw = tw[1:]
                    do_nlprune = False
                    if len(tw) != tw.count('\n'):
                        nlstate = 0
                    if tw.endswith('\n\n'):
                        nlstate += 2
                    elif tw.endswith('\n'):
                        nlstate += 1
                f.write(tw)
            f.truncate()
        # Report them.
        ret = (not excess and not redundant)
        if ret and writeback: ret = Ellipsis
        if not ret and warn:
            for ent in parts:
                if not isinstance(ent, tuple) or ent[0] != 'import':
                    continue
                impdatum = imports[ent[1]]
                if impdatum is None: continue
                bn = trailing_name(impdatum[1])
                if bn in excess:
                    sys.stderr.write('%s:%s: warning: superfluous import '
                        'of %s\n' % (filename, ent[2], impdatum[1]))
                elif impdatum[1] in redundant:
                    sys.stderr.write('%s:%s: warning: redundant import '
                        'of %s\n' % (filename, ent[2], impdatum[1]))
    return ret

def main():
    """
    Main function
    """
    warn, sort, prune, empty_lines = True, False, False, False
    report = False
    in_args, filenames = False, []
    for arg in sys.argv[1:]:
        if not in_args and arg.startswith('-'):
            if arg == '--':
                in_args = True
            elif arg == '--help':
                sys.stderr.write('USAGE: %s %s [--report|--report-null|'
                    '--no-report] <file(s)>\n' % (sys.argv[0], ' '.join(
                        '[--[no-]%s]' % x for x in ('warn', 'sort', 'prune',
                                                    'empty-lines'))))
                sys.stderr.write('Default is --warn only.\n'
                    '--warn       : Report unnecessary imports.\n'
                    '--sort       : Lexicographically sort the imports '
                        '(modifies file).\n'
                    '--prune      : Remove unnecessary imports (modifies '
                        'file; negates --warn).\n'
                    '--empty-lines: Strip double blank lines arising from '
                        'import removal to single ones (only effective if '
                        '--prune is).\n'
                    '--report     : Print names of modified/warned-about '
                        'files (newline-terminated).\n'
                    '--report-null: Print names of modified/warned-about '
                        'files (NUL-terminated).\n')
                sys.exit(0)
            elif arg == '--warn':
                warn = True
            elif arg == '--no-warn':
                warn = False
            elif arg == '--sort':
                sort = True
            elif arg == '--no-sort':
                sort = False
            elif arg == '--prune':
                prune = True
            elif arg == '--no-prune':
                prune = False
            elif arg == '--empty-lines':
                empty_lines = True
            elif arg == '--no-empty-lines':
                empty_lines = False
            elif arg == '--report':
                report = True
            elif arg == '--report-null':
                report = Ellipsis
            elif arg == '--no-report':
                report = False
            else:
                sys.stderr.write('Unknown option %r!\n' % arg)
                sys.exit(1)
            continue
        filenames.append(arg)
    checked, res = set(), True
    for f in filenames:
        if f in checked: continue
        checked.add(f)
        r = importlint(f, warn=warn, sort=sort, prune=prune,
                       empty_lines=empty_lines)
        if not r or r is Ellipsis:
            if report is Ellipsis:
                sys.stdout.write(f + '\0')
                sys.stdout.flush()
            elif report:
                sys.stdout.write(f + '\n')
                sys.stdout.flush()
        if not r:
            res = False
    sys.exit(0 if res else 2)

if __name__ == '__main__': main()
