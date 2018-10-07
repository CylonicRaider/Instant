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
    def __init__(self, name, cmdline, pidfile):
        self.name = name
        self.cmdline = cmdline
        self.pidfile = pidfile
        self._pid = Ellipsis
        self._child = None

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
                                       shell=True)
        self.set_pid(self._child.pid)
        return 'OK'

    def stop(self, wait=True):
        status = None
        if self._child is not None:
            self._child.terminate()
            if wait:
                status = self._child.wait()
            self._child = None
        else:
            pid = self.get_pid()
            if pid is None:
                return 'NOT_RUNNING'
            else:
                os.kill(pid, signal.SIGTERM)
        self.set_pid(None)
        return 'OK' + ('' if status is None else ' %s' % (status,))

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
                print ('%s: %s' % (p.name, result))

    def start(self, verbose=True):
        self._for_each(lambda p: p.start(), verbose)

    def stop(self, wait=True, verbose=True):
        self._for_each(lambda p: p.stop(wait), verbose)

    def status(self, verbose=True):
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
            try:
                pidfile = values['pidfile']
            except KeyError:
                pidfile = DEFAULT_PIDFILE_TEMPLATE % s
            self.add(Process(s, cmdline, pidfile))

    def dispatch(self, cmd):
        try:
            func = {'start': self.start, 'stop': self.stop,
                    'status': self.status}[cmd]
        except KeyError:
            raise RunnerError('Unknown command: ' + cmd)
        func()

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
