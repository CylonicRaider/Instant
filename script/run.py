#!/usr/bin/env python3
# -*- coding: ascii -*-

# An init script for running Instant and a number of bots.

import os, re, time
import errno, signal
import shlex
import subprocess
import argparse

import coroutines

try: # Py3K
    from configparser import ConfigParser as _ConfigParser
except ImportError: # Py2K
    from ConfigParser import SafeConfigParser as _ConfigParser

DEFAULT_CONFFILE = 'config/instant.ini'
DEFAULT_PIDFILE_TEMPLATE = 'run/%s.pid'

REDIRECTION_RE = re.compile(r'^[<>|&]+')
PID_LINE_RE = re.compile(r'^[0-9]+\s*$')

class RunnerError(Exception): pass

class ConfigurationError(RunnerError): pass

def open_mkdirs(path, mode, do_mkdirs=True):
    try:
        return open(path, mode)
    except IOError as e:
        if not (e.errno == errno.ENOENT and ('a' in mode or 'w' in mode) and
                do_mkdirs):
            raise
    try:
        os.makedirs(os.path.dirname(path))
    except IOError as e:
        if e.errno != errno.EEXIST:
            raise
    return open(path, mode)

class VerboseExit(coroutines.Exit):
    def __init__(self, value, verbose=False, context=None):
        coroutines.Exit.__init__(self, value)
        self.verbose = verbose
        self.context = context

    def log(self):
        if not self.verbose: return
        context_str = '' if self.context is None else '%s: ' % (self.context,)
        print ('%s%s' % (context_str, self.result))

    def apply(self, wake, executor, routine):
        self.log()
        coroutines.Exit.apply(self, wake, executor, routine)

class Redirection:
    @classmethod
    def parse(cls, text):
        text = text.strip()
        try:
            constant = {'': None,
                        'stdout': subprocess.STDOUT,
                        'devnull': subprocess.DEVNULL}[text]
        except KeyError:
            pass
        else:
            return cls(constant)
        m = REDIRECTION_RE.match(text)
        if not m:
            raise ValueError('Invalid redirection string: %r' % (text,))
        prefix, filename = m.group(0), text[m.end():].strip()
        try:
            mode = {'<': 'rb', '>': 'wb', '>>': 'ab', '<>': 'r+b'}[prefix]
        except KeyError:
            raise ValueError('Invalid redirection operator: %s' % prefix)
        return cls(filename, mode)

    def __init__(self, target, mode=None):
        self.target = target
        self.mode = mode
        self.mkdirs = True

    def open(self, _retry=True):
        if isinstance(self.target, str):
            return open_mkdirs(self.target, self.mode, self.mkdirs)
        else:
            return self.target

    def close(self, fp):
        if hasattr(fp, 'close'):
            try:
                fp.close()
            except Exception:
                pass

class Process:
    def __init__(self, name, command, config=None):
        if config is None: config = {}
        self.name = name
        self.command = command
        self.pidfile = config.get('pidfile', DEFAULT_PIDFILE_TEMPLATE % name)
        self.workdir = config.get('workdir')
        self.stdin = Redirection.parse(config.get('stdin', ''))
        self.stdout = Redirection.parse(config.get('stdout', ''))
        self.min_stop = float(config.get('min-stop', 0))
        self.stderr = Redirection.parse(config.get('stderr', ''))
        self._pid = Ellipsis
        self._child = None

    def _redirectors(self):
        return (self.stdin, self.stdout, self.stderr)

    def _read_pidfile(self):
        f = None
        try:
            f = open(self.pidfile)
            data = f.read()
            if not PID_LINE_RE.match(data):
                raise ValueError('Invalid PID file contents')
            ret = int(data)
            if ret < 0:
                raise ValueError('Invalid PID in PID file')
            return ret
        except IOError as e:
            if e.errno == errno.ENOENT: return None
            raise
        finally:
            if f: f.close()

    def _write_pidfile(self, pid):
        if pid is None:
            try:
                os.unlink(self.pidfile)
            except OSError as e:
                if e.errno == e.ENOENT: return
                raise
        else:
            with open_mkdirs(self.pidfile, 'w') as f:
                f.write('%s\n' % pid)

    def get_pid(self, force=False):
        if self._pid is Ellipsis or force:
            self._pid = self._read_pidfile()
        return self._pid

    def set_pid(self, pid):
        self._pid = pid
        self._write_pidfile(pid)

    def _make_exit(self, operation, verbose):
        def callback(value):
            return VerboseExit(value, verbose, context)
        if verbose and operation:
            context = '%s (%s)' % (self.name, operation)
        elif verbose:
            context = self.name
        else:
            context = None
        return callback

    def start(self, wait=True, verbose=False):
        exit = self._make_exit('start', verbose)
        try:
            cur_status = self.status()
            if cur_status == 'RUNNING':
                yield exit('ALREADY_RUNNING')
        except IOError:
            pass
        files = []
        try:
            # NOTE: The finally clause relies on every file being added to
            #       files as soon as it is created -- [r.open() for r in
            #       self._redirectors()] may leak file descriptors.
            for r in self._redirectors():
                files.append(r.open())
            self._child = subprocess.Popen(self.command, cwd=self.workdir,
                close_fds=False, stdin=files[0], stdout=files[1],
                stderr=files[2])
        finally:
            for r, f in zip(self._redirectors(), files):
                r.close(f)
        self.set_pid(self._child.pid)
        yield exit('OK')

    def stop(self, wait=True, verbose=False):
        exit = self._make_exit('stop', verbose)
        prev_child = self._child
        if self._child is not None:
            self._child.terminate()
            self._child = None
        else:
            # We could theoretically wait for the PID below, but that would be
            # even more fragile than what we already do in status().
            pid = self.get_pid()
            if pid is None:
                yield exit('NOT_RUNNING')
            else:
                os.kill(pid, signal.SIGTERM)
        self.set_pid(None)
        if wait:
            if prev_child:
                raw_status = yield coroutines.WaitProcess(prev_child)
                status = 'OK %s' % (raw_status,)
            else:
                status = None
            delay = self.min_stop
            if delay > 0: yield coroutines.Sleep(delay)
        else:
            status = 'OK'
        yield exit(status)

    def status(self, verbose=True):
        exit = self._make_exit(None, verbose)
        pid = self.get_pid()
        if pid is None:
            yield exit('NOT_RUNNING')
        try:
            os.kill(pid, 0)
            yield exit('RUNNING')
        except OSError as e:
            if e.errno == errno.ESRCH: yield exit('STALEFILE')
            raise

class ProcessGroup:
    def __init__(self):
        self.processes = []

    def add(self, proc):
        self.processes.append(proc)

    def _for_each(self, handler):
        calls = [coroutines.Call(handler(p)) for p in self.processes]
        result = yield coroutines.All(*calls)
        yield coroutines.Exit(result)

    def start(self, wait=True, verbose=False):
        return self._for_each(lambda p: p.start(wait, verbose))

    def stop(self, wait=True, verbose=False):
        return self._for_each(lambda p: p.stop(wait, verbose))

    def status(self, verbose=True):
        return self._for_each(lambda p: p.status(verbose))

class InstantManager(ProcessGroup):
    def __init__(self, conffile=None):
        ProcessGroup.__init__(self)
        if conffile is None: conffile = DEFAULT_CONFFILE
        self.conffile = conffile
        self.config = None

    def init(self):
        self.config = _ConfigParser()
        self.config.read(self.conffile)
        sections = [s for s in self.config.sections()
                    if s == 'instant' or s.startswith('scribe-')]
        sections.sort()
        for s in sections:
            values = dict(self.config.items(s))
            try:
                cmdline = values['cmdline']
            except KeyError:
                raise ConfigurationError('Missing required key "cmdline" in '
                    'section "%s"' % s)
            command = tuple(shlex.split(cmdline))
            self.add(Process(s, command, values))

    def dispatch(self, cmd, arguments=None):
        try:
            func = {'start': self.do_start, 'stop': self.do_stop,
                    'restart': self.do_restart, 'status': self.do_status}[cmd]
        except KeyError:
            raise RunnerError('Unknown command: ' + cmd)
        kwds = {}
        if cmd in ('start', 'stop'):
            kwds['wait'] = getattr(arguments, 'wait')
        func(**kwds)

    def _run_routine(self, routine):
        coroutines.run([routine], sigpipe=True)

    def do_start(self, wait=True):
        self._run_routine(self.start(verbose=True))

    def do_stop(self, wait=True):
        self._run_routine(self.stop(verbose=True))

    def do_restart(self):
        self.do_stop()
        self.do_start()

    def do_status(self):
        self._run_routine(self.status(verbose=True))

def main():
    p = argparse.ArgumentParser(
        description='Manage an Instant backend and a group of bots')
    p.add_argument('--config', '-c', default=DEFAULT_CONFFILE,
                   help='Configuration file location (default %(default)s)')
    sp = p.add_subparsers(dest='cmd', description='The action to perform')
    p_start = sp.add_parser('start', help='Start the backend and bots')
    p_stop = sp.add_parser('stop', help='Stop the backend and bots')
    p_restart = sp.add_parser('restart',
                              help='Perform "stop" and then "start"')
    p_status = sp.add_parser('status',
                             help='Check whether backend or bots are running')
    p_start.add_argument('--no-wait', action='store_false', dest='wait',
                         help='Exit immediately after commencing the start')
    p_stop.add_argument('--no-wait', action='store_false', dest='wait',
                        help='Exit immediately after commencing the stop')
    arguments = p.parse_args()
    mgr = InstantManager(arguments.config)
    try:
        mgr.init()
    except ConfigurationError as exc:
        raise SystemExit('Configuration error: ' + str(exc))
    mgr.dispatch(arguments.cmd, arguments)

if __name__ == '__main__': main()
