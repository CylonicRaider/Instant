#!/usr/bin/env python3
# -*- coding: ascii -*-

"""
Utility script for embedding additional class files into JAR files.
"""

import sys, os
import fnmatch
import zipfile
import subprocess

try: # Py3K
    from configparser import ConfigParser, NoOptionError
except ImportError: # Py2K
    from ConfigParser import SafeConfigParser as ConfigParser, NoOptionError

def parse_config(filename):
    def make_getter(secname):
        def get_value(key, default=None):
            try:
                return parser.get(secname, key)
            except NoOptionError:
                return default
        return get_value
    parser = ConfigParser()
    if not parser.read(filename):
        raise SystemExit('ERROR: Could not find configuration file')
    base_dir = os.path.abspath(os.path.dirname(filename))
    ret = {}
    for secname in parser.sections():
        get = make_getter(secname)
        dest = os.path.normpath(os.path.join(base_dir, get('dest-base', ''),
                                             secname))
        src_base = os.path.normpath(os.path.join(base_dir,
                                                 get('src-base', '')))
        transcludes = [(src_base, path)
                       for path in get('transclude', '').split()]
        filters = get('filter', '').split()
        ret[dest] = {'transcludes': transcludes, 'filters': filters,
                     'get': get}
    return ret

def _null_config(key, default=None):
    return default

def _resolve_transcludes(transcludes, filters):
    for srcbase, basepath in transcludes:
        srcdir = os.path.join(srcbase, basepath)
        for dirpath, dirnames, filenames in os.walk(srcdir):
            reldir = os.path.relpath(dirpath, srcbase)
            for fn in sorted(filenames):
                if any(fnmatch.fnmatch(fn, pattern) for pattern in filters):
                    relpath = os.path.normpath(os.path.join(reldir, fn))
                    yield (srcbase, relpath)

def transclude(dest, transcludes, filters, config=None):
    with zipfile.ZipFile(dest, 'a', zipfile.ZIP_DEFLATED) as destfile:
        for srcdir, relpath in _resolve_transcludes(transcludes, filters):
            destfile.write(os.path.join(srcdir, relpath), relpath)

def transclude_jar(dest, transcludes, filters, config=None):
    def flush(srcdir, toadd):
        if srcdir is None: return
        os.chdir(srcdir)
        cmdline = [jar_path, 'uf', dest] + toadd
        subprocess.check_call(cmdline)
    if config is None: config = _null_config
    jar_path = config('jar', 'jar') # Binks!
    last_srcdir, toadd = None, None
    # We buffer all resolution results to avoid changing directory while an
    # os.walk() is in progress.
    for srcdir, relpath in tuple(_resolve_transcludes(transcludes, filters)):
        if srcdir != last_srcdir:
            flush(last_srcdir, toadd)
            last_srcdir, toadd = srcdir, []
        toadd.append(relpath)
    flush(last_srcdir, toadd)

def main():
    # Parse command line.
    confpath, files, jarmode, quiet = None, [], False, False
    try:
        it, only_args = iter(sys.argv[1:]), False
        for arg in it:
            if not only_args and arg.startswith('-'):
                if arg == '--':
                    only_args = True
                elif arg == '--help':
                    sys.stderr.write('USAGE: %s [--help] [--[no-]jar] '
                        '[--[no-]quiet] --config CONFFILE '
                        'PATH1 [PATH2 [...]]\n' % sys.argv[0])
                    sys.stderr.write(
                        'Incorporate files matching a pattern into a ZIP (or '
                            'JAR) file.\n'
                        '--help  : This help.\n'
                        '--jar   : Invoke the "jar" tool for adding files '
                            '(instead of using Python\'s ZIP file support).\n'
                        '--quiet : Only print error messages.\n'
                        '--config: Read configuration file at this path.\n'
                        'PATHn   : Process this file according to the '
                            'corresponding configuration section.\n')
                    raise SystemExit
                elif arg == '--config':
                    confpath = next(it)
                elif arg == '--jar':
                    jarmode = True
                elif arg == '--no-jar':
                    jarmode = False
                elif arg == '--quiet':
                    quiet = True
                elif arg == '--no-quiet':
                    quiet = False
                else:
                    raise SystemExit('ERROR: Unknown option %s' % arg)
            else:
                files.append(arg)
    except StopIteration:
        raise SystemExit('ERROR: Missing required value for option %s' % arg)
    if confpath is None:
        raise SystemExit('ERROR: Configuration file not specified')
    # Load configuration.
    try:
        config = parse_config(confpath)
    except Exception as exc:
        raise SystemExit('ERROR: Could not load configuration file (%s: %s)' %
                         (exc.__class__.__name__, exc))
    # Transclude!
    transcluder = transclude_jar if jarmode else transclude
    for filename in files:
        absfilename = os.path.abspath(filename)
        this_conf = config.get(absfilename)
        if not this_conf:
            continue
        if not quiet:
            print ('Transcluding files into %s...' % filename)
        transcluder(absfilename, this_conf['transcludes'],
                    this_conf['filters'], this_conf['get'])

if __name__ == '__main__': main()
