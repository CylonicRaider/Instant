#!/usr/bin/env python3
# -*- coding: ascii -*-

# An init script for running Instant and a number of bots.

import os, re
import errno, signal
import subprocess
import argparse

try: # Py3K
    from configparser import ConfigParser as _ConfigParser
except ImportError: # Py2K
    from ConfigParser import SafeConfigParser as _ConfigParser

DEFAULT_CONFFILE = 'config/instant.ini'
DEFAULT_PIDFILE_TEMPLATE = 'run/%s.pid'

PID_LINE_RE = re.compile(r'^[0-9]+\s*$')

class RunnerError(Exception): pass

class ConfigurationError(RunnerError): pass

class Process:
    def __init__(self, name, cmdline, config=None):
        if config is None: config = {}
        self.name = name
        self.cmdline = cmdline
        self.pidfile = config.get('pidfile', DEFAULT_PIDFILE_TEMPLATE % name)
        self.workdir = config.get('workdir')
        self._pid = Ellipsis
        self._child = None
        self._prev_child = None

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
            with open(self.pidfile, 'w') as f:
                f.write('%s\n' % pid)

    def get_pid(self, force=False):
        if self._pid is Ellipsis or force:
            self._pid = self._read_pidfile()
        return self._pid

    def set_pid(self, pid):
        self._pid = pid
        self._write_pidfile(pid)

    def start(self):
        try:
            cur_status = self.status()
            if cur_status == 'RUNNING':
                return 'ALREADY_RUNNING'
        except IOError:
            pass
        self._child = subprocess.Popen(self.cmdline, close_fds=False,
                                       shell=True, cwd=self.workdir)
        self.set_pid(self._child.pid)
        return 'OK'

    def wait_start(self):
        return None

    def stop(self):
        if self._child is not None:
            self._child.terminate()
            self._prev_child = self._child
            self._child = None
        else:
            # We could theoretically record the PID for wait_stop(), but that
            # would be even more fragile than what we already do in status().
            self._prev_child = None
            pid = self.get_pid()
            if pid is None:
                return 'NOT_RUNNING'
            else:
                os.kill(pid, signal.SIGTERM)
        self.set_pid(None)
        return 'OK'

    def wait_stop(self):
        if self._prev_child:
            raw_status = self._prev_child.wait()
            status = 'OK %s' % (raw_status,)
            self._prev_child = None
        else:
            status = None
        return status

    def status(self):
        pid = self.get_pid()
        if pid is None:
            return 'NOT_RUNNING'
        try:
            os.kill(pid, 0)
            return 'RUNNING'
        except OSError as e:
            if e.errno == errno.ESRCH: return 'STALEFILE'
            raise

class ProcessGroup:
    def __init__(self):
        self.processes = []

    def add(self, proc):
        self.processes.append(proc)

    def _for_each(self, handler, verbose=True):
        for p in self.processes:
            try:
                result = handler(p)
            except Exception as exc:
                result = 'ERROR (%s: %s)' % (type(exc).__name__, exc)
            if verbose:
                if result is None: result = 'NA'
                print ('%s: %s' % (p.name, result))

    def start(self, verbose=False):
        self._for_each(lambda p: p.start(), verbose)

    def wait_start(self, verbose=False):
        self._for_each(lambda p: p.wait_start(), verbose)

    def stop(self, verbose=False):
        self._for_each(lambda p: p.stop(), verbose)

    def wait_stop(self, verbose=False):
        self._for_each(lambda p: p.wait_stop(), verbose)

    def status(self, verbose=False):
        self._for_each(lambda p: p.status(), verbose)

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
            self.add(Process(s, cmdline, values))

    def dispatch(self, cmd):
        try:
            func = {'start': self.do_start, 'stop': self.do_stop,
                    'status': self.do_status}[cmd]
        except KeyError:
            raise RunnerError('Unknown command: ' + cmd)
        func()

    def do_start(self):
        self.start(verbose=True)

    def do_stop(self):
        self.stop(verbose=False)

    def do_status(self):
        self.status(verbose=True)

def main():
    p = argparse.ArgumentParser(
        description='Manage an Instant backend and a group of bots')
    p.add_argument('--config', '-c', default=DEFAULT_CONFFILE,
                   help='Configuration file location (default %(default)s)')
    sp = p.add_subparsers(dest='cmd', description='The action to perform')
    sp.add_parser('start', help='Start the backend and bots')
    sp.add_parser('stop', help='Stop the backend and bots')
    sp.add_parser('status', help='Check whether backend or bots are running')
    arguments = p.parse_args()
    mgr = InstantManager(arguments.config)
    try:
        mgr.init()
    except ConfigurationError as exc:
        raise SystemExit('Configuration error: ' + str(exc))
    mgr.dispatch(arguments.cmd)

if __name__ == '__main__': main()
