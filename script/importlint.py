#!/usr/bin/env python3
# -*- coding: ascii -*-

"""
A script cleaning up imports in Java source files.
"""

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
def join_names(a, b):
    "Combine the two identifiers, possibly inserting a dot between them"
    if a and b:
        return a + '.' + b
    elif a:
        return a
    elif b:
        return b
    else:
        return ''

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
    lineno, ret, imports, idents, package = 1, [], [], [], ''
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
        lineno += tok.group().count('\n')
    return {None: ret, 'import': imports, 'ident': idents,
            'package': package}

def importlint(filename, warn=True, sort=False, prune=False,
               empty_lines=False, files=None, deps=None, warn_files=False):
    """
    Perform various operations on the given Java source file

    The file is always checked for superfluous imports, they are optionally
    warned about, optionally pruned, the remaining imports are optionally
    sorted, and double blank lines resulting from the pruning step are
    optionally coalesced. If files is not None, it is taken to be be a
    mapping from class names to file names and the public class defined by the
    given file is recorded into it. If deps is not None, it is taken to be a
    mapping from class names to sets of class names, and the entry
    corresponding to the public class defined by the given file is populated
    by any classes the file *might potentially* refer to (due to the
    superficial parsing performed by this tool, this will be a very rough
    over-estimate). If warn_files is true, this will complain if multiple
    source files appear to represent the same public class.
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
        classname = join_names(package,
                               leading_name(os.path.basename(filename)))
        # Enumerate used and imported names.
        used = set(leading_name(n[1]) for n in idents)
        imported, impmap, redundant = set(), dict(), set()
        for n in imports:
            pref, sep, bn = n[1].rpartition('.')
            imported.add(bn)
            impmap[bn] = pref
            if sep and pref in (package, 'java.lang'):
                redundant.add(n[1])
        excess = imported.difference(used)
        remove = excess.union(trailing_name(n) for n in redundant)
        fqused = set(n[1] for n in idents if '.' in n[1])
        fqused.update(join_names(impmap.get(bn, package), bn) for bn in used)
        # Enter data into files and deps.
        if files is not None:
            if classname in files and warn_files:
                sys.stderr.write('%s: warning: another file declares the '
                    'same class\n'
                    '%s: note: previously declared here\n' %
                    (filename, files[classname]))
            files[classname] = filename
        if deps is not None:
            deps.setdefault(classname, set()).update(fqused)
        # Reomove excess imports.
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
        # Sort the remaining imports.
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
        # Overwrite the file.
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

def gather_deps(files, rawdeps):
    """
    Aggregate the dependency information collected by importlint() and
    optionally print it out in Make format
    """
    def visit_scc(cn):
        "Helper function for strongly connected component search"
        # We use the length of lowlinks instead of a global counter because
        # of the scoping rules of Python 2 (which we want to remain compatible
        # to).
        indices[cn] = len(lowlinks)
        lowlinks[cn] = len(lowlinks)
        stack.append(cn)
        stackset.add(cn)
        # Now, look through the 'successors' of cn.
        for d in deps.get(cn, ()):
            if d not in indices:
                visit_scc(d)
                lowlinks[cn] = min(lowlinks[cn], lowlinks[d])
            elif d in stackset:
                lowlinks[cn] = min(lowlinks[cn], indices[d])
        # If the current node does not refer to a predecessor on the stack,
        # we have finished traversing a strongly connected component and can
        # recover it from the stack.
        if lowlinks[cn] == indices[cn]:
            scc = set()
            scc_groups[cn] = scc
            while 1:
                d = stack.pop()
                stackset.remove(d)
                scc.add(d)
                rev_scc_groups[d] = cn
                if d == cn: break
    def visit_fd(rcn):
        "Helper function for transitive dependency enumeration"
        if rcn in fulldeps:
            return
        fdl = set(scc_groups[rcn])
        for rd in scc_deps[rcn]:
            if rd == rcn: continue
            visit_fd(rd)
            fdl.update(fulldeps[rd])
        for cn in scc_groups[rcn]:
            fulldeps[cn] = fdl
    # Filter out bogus dependencies.
    deps = {}
    for cn, dl in rawdeps.items():
        deps[cn] = set(d for d in dl if d in files)
        deps[cn].add(cn)
    # We use Tarjan's pertinent algorithm to locate the strongly connected
    # components of the dependency graph; every node in a strongly connected
    # component has (because of the strong connectedness) the same set of
    # dependencies.
    indices, lowlinks, stack, stackset = {}, {}, [], set()
    scc_groups, rev_scc_groups = {}, {}
    for cn in deps:
        if cn not in indices:
            visit_scc(cn)
    # We contract the SCCs to single representative vertices to simplify the
    # next step.
    scc_deps = {}
    for cn, dl in deps.items():
        scc_deps.setdefault(rev_scc_groups[cn], set()).update(
            rev_scc_groups[d] for d in deps[cn])
    # Finally, we can traverse the resulting (acyclic) graph to collect the
    # transitive dependencies of every node.
    fulldeps = {}
    for rcn in scc_deps:
        visit_fd(rcn)
    # Map the class names to file names.
    filedeps = {}
    for cn, dl in fulldeps.items():
        filedeps[files[cn]] = set(files[d] for d in dl)
    # Done.
    return filedeps

def main():
    """
    Main function
    """
    warn, sort, prune, empty_lines = True, False, False, False
    deps, report = False, False
    in_args, filenames = False, []
    for arg in sys.argv[1:]:
        if not in_args and arg.startswith('-'):
            if arg == '--':
                in_args = True
            elif arg == '--help':
                sys.stderr.write('USAGE: %s [--help] %s [--report|'
                    '--report-null|--no-report] <file(s)>\n' % (sys.argv[0],
                        ' '.join('[--[no-]%s]' % x for x in ('warn', 'sort',
                            'prune', 'empty-lines', 'deps'))))
                sys.stderr.write('Test Java source files for superfluous '
                        'imports and remove them if necessary, and also '
                        'discover dependencies among them.\nDefault is '
                        '--warn only.\n'
                    '--help       : This help.\n'
                    '--warn       : Report unnecessary imports.\n'
                    '--sort       : Lexicographically sort the imports '
                        '(modifies file).\n'
                    '--prune      : Remove unnecessary imports (modifies '
                        'file; negates --warn).\n'
                    '--empty-lines: Strip double blank lines arising from '
                        'import removal to single ones (only effective if '
                        '--prune is).\n'
                    '--deps       : Gather and output Make-style dependency '
                        'information among (and only among) the specified '
                        'files (conflicts with --report and --report-null).\n'
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
            elif arg == '--deps':
                deps = True
            elif arg == '--no-deps':
                deps = False
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
    if deps and report:
        sys.stderr.write('Cannot report dependencies and modified files at '
            'the same time.\n')
        sys.exit(1)
    checked, res = set(), True
    filemap, depmap = {}, {}
    for f in filenames:
        if f in checked: continue
        checked.add(f)
        r = importlint(f, warn=warn, sort=sort, prune=prune,
                       empty_lines=empty_lines, files=filemap, deps=depmap,
                       warn_files=deps)
        if not r or r is Ellipsis:
            if report is Ellipsis:
                sys.stdout.write(f + '\0')
                sys.stdout.flush()
            elif report:
                sys.stdout.write(f + '\n')
                sys.stdout.flush()
        if not r:
            res = False
    if deps:
        fulldeps = gather_deps(filemap, depmap)
        for fn in sorted(fulldeps):
            sys.stdout.write('%s:%s\n' % (fn, ''.join(' ' + dn
                for dn in sorted(fulldeps[fn]))))
        sys.stdout.flush()
    sys.exit(0 if res else 2)

if __name__ == '__main__': main()
