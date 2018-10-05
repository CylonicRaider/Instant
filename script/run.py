#!/usr/bin/env python3
# -*- coding: ascii -*-

# An init script for running Instant and a number of bots.

import re

PID_LINE_RE = re.compile(r'^[0-9]+\s*$')

class Process:
    def __init__(self, name, pidfile):
        self.name = name
        self.pidfile = pidfile
        self._pid = Ellipsis

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
        with open(self.pidfile, 'w') as f:
            f.write('%s\n' % pid)

    def get_pid(self, force=False):
        if self._pid is Ellipsis or force:
            self._pid = self._read_pidfile()
        return self._pid

    def set_pid(self, pid):
        self._pid = pid
        self._write_pidfile(pid)

    def status(self):
        pid = self.get_pid()
        if pid is None:
            return 'DEAD'
        try:
            os.kill(pid, 0)
            return 'RUNNING'
        except OSError as e:
            if e.errno == errno.ESRCH: return 'STALEFILE'
            raise

def main():
    pass

if __name__ == '__main__': main()
