#!/usr/bin/env python3
# -*- coding: ascii -*-

"""
A script for locating Java source files in need of a recompile.
"""

import sys, os, re

COMMENT_RE = re.compile(r'^\s*(#.*)?$')
MAP_LINE_RE = re.compile(r'^([^\s:#]+):((?:\s*[^\s:#]+)*)\s*$')

class FSCache:
    "A caching wrapper around select filesystem operations"

    def __init__(self):
        "Instance initializer"
        self.stats = {}
        self.listings = {}

    def stat(self, path):
        "Caching counterpart of os.stat()"
        try:
            return self.stats[path]
        except KeyError:
            st = os.stat(path)
            self.stats[path] = st
            return st

    def listdir(self, path):
        "Caching counterpart of os.listdir()"
        try:
            return self.listings[path]
        except KeyError:
            ls = os.listdir(path or '.')
            self.listings[path] = ls
            return ls

    def getmtime(self, path):
        "Convenience wrapper around stat(path).st_mtime"
        return self.stat(path).st_mtime

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

def path_matches(filename, dirname):
    "Check whether filename is inside dirname (..-s notwithstanding)"
    ld = len(dirname)
    return (filename.startswith(dirname) and
            (dirname.endswith(os.path.sep) or
             filename[ld:ld + 1] in ('', os.path.sep)))

def filter_paths(files, dirs):
    "Return those elements of files that match dirs (or all if dirs is None)"
    if dirs is None:
        return list(files)
    else:
        return [f for f in files if any(path_matches(f, d) for d in dirs)]

def find_classes(fs, path):
    "Locate the class file(s) belonging to the given Java source file"
    if not path.endswith('.java'): return ()
    dirname = os.path.dirname(path)
    siblings = fs.listdir(dirname)
    pathbase = os.path.basename(path)[:-5]
    nested_prefix = pathbase + '$'
    return [os.path.join(dirname, p) for p in siblings
            if p.endswith('.class') and (p[:-6] == pathbase or
                                         p.startswith(nested_prefix))]

def check_build(files, depmap):
    "Locate those of the given files that need be rebuilt"
    fs = FSCache()
    ret = {}
    for path in files:
        if path in ret or not depmap.get(path): continue
        classes = find_classes(fs, path)
        if classes:
            # If there are classes, we can check them for staleness.
            oldest_class = min(fs.getmtime(cp) for cp in classes)
            newest_dep = max(fs.getmtime(dp) for dp in depmap[path])
            if newest_dep > oldest_class: ret[path] = classes
        elif path.endswith('.java'):
            # Otherwise (if this *is* a Java source file), there *ought* to
            # be classes.
            ret[path] = []
    return ret

def summarize(filelist):
    "Bring a list of potentially redundant paths into a compact form"
    ret, index = [], {}
    for entry in filelist:
        dirname, basename = os.path.split(entry)
        prefix = os.path.join(dirname, '')
        stem, ext = os.path.splitext(basename)
        if (prefix, ext) in index:
            index[prefix, ext].append(stem)
        else:
            index[prefix, ext] = [stem]
            ret.append((prefix, index[prefix, ext], ext))
    return ret

def main():
    # Parse command line.
    mapfile, cleanup, report, filters = None, False, False, []
    try:
        it, args_only = iter(sys.argv[1:]), False
        for opt in it:
            if args_only or not opt.startswith('-'):
                filters.append(opt)
            elif opt == '--':
                args_only = True
            elif opt == '--help':
                sys.stderr.write('USAGE: %s [--help] [--[no-]cleanup] '
                        '[--[no-]report] [--map MAPFILE] [DIR [DIR ...]]\n'
                    'Locate Java source files needing a recompile and print '
                        'their paths to standard output.\n'
                    '--help   : Display help.\n'
                    '--cleanup: Also delete stale class files.\n'
                    '--report : Print a human-readable message about the '
                        'files to be rebuilt to standard error.\n'
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
            elif opt == '--report':
                report = True
            elif opt == '--no-report':
                report = False
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
    files = filter_paths(depmap, filters or None)
    # Perform the actual build checking.
    tobuild = check_build(files, depmap)
    buildlist = sorted(tobuild)
    # Report information for the user.
    if report and buildlist:
        words = []
        for prefix, bases, suffix in summarize(buildlist):
            if len(bases) == 1:
                words.append(prefix + bases[0] + suffix)
            else:
                words.append('%s{%s}%s' % (prefix, ','.join(bases), suffix))
        sys.stderr.write('Recompiling ' + ' '.join(words) + '...\n')
        sys.stderr.flush()
    # Perform class file cleanup.
    if cleanup:
        for cfl in tobuild.values():
            for cf in cfl:
                os.unlink(cf)
    # Generate main output.
    for sf in buildlist:
        print (sf)

if __name__ == '__main__': main()
