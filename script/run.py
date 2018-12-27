#!/usr/bin/env python3
# -*- coding: ascii -*-

# An init script for running Instant and a number of bots.

import sys, os, re, time, inspect
import string, uuid
import errno, signal, socket
import shlex
import logging
import subprocess
import argparse

import coroutines

try: # Py3K
    from configparser import ConfigParser as _ConfigParser, \
        NoSectionError as _NoSectionError
except ImportError: # Py2K
    from ConfigParser import SafeConfigParser as _ConfigParser, \
        NoSectionError as _NoSectionError

DEFAULT_CONFFILE = 'config/instant.ini'
DEFAULT_COMM_PATH = 'run/comm'
DEFAULT_PIDFILE_TEMPLATE = 'run/%s.pid'
WARMUP_PIDFILE_SUFFIX = '.new'
PROCESS_SECTIONS = ('proc', 'instant', 'bot')

REDIRECTION_RE = re.compile(r'^[<>|&]+')
PID_LINE_RE = re.compile(r'^[0-9]+\s*$')

ESCAPE_PARSE_RE = re.compile(r' |\\.?')
ESCAPE_CHARS = {'\\\\': '\\', '\\ ': ' ', '\\n': '\n', '\\z': ''}

THIS_FILE = os.path.abspath(inspect.getfile(lambda: None))

class RunnerError(Exception): pass

class ConfigurationError(RunnerError): pass

class NoSuchProcessesError(RunnerError):
    def __init__(self, proclist):
        RunnerError.__init__(self, 'Unknown process%s: %s' % (
                             'es' if len(proclist) != 1 else '',
                             ', '.join(map(str, sorted(proclist)))))
        self.proclist = proclist

class RemoteError(RunnerError):
    def __init__(self, code, message):
        RunnerError.__init__(self, '[%s] %s' % (code, message))
        self.code = code
        self.message = message

def is_true(s):
    if isinstance(s, str):
        return s.lower() in ('1', 'y', 'yes', 'true')
    else:
        return bool(s)

def find_dict_key(data, value):
    for k, v in data.items():
        if v == value:
            return k
    else:
        raise LookupError(value)

def safe_makedirs(path):
    try:
        os.makedirs(path)
    except OSError as e:
        if e.errno == errno.EEXIST:
            return False
        raise
    else:
        return True

def open_mkdirs(path, mode, do_mkdirs=True):
    try:
        return open(path, mode)
    except IOError as e:
        if not (e.errno == errno.ENOENT and ('a' in mode or 'w' in mode) and
                do_mkdirs):
            raise
    safe_makedirs(os.path.dirname(path))
    return open(path, mode)

class VerboseExit(coroutines.Exit):
    def __init__(self, result, verbose=False, context=None):
        coroutines.Exit.__init__(self, result)
        self.verbose = verbose
        self.context = context

    def log(self):
        if not self.verbose: return
        context_str = '' if self.context is None else '%s: ' % (self.context,)
        result_str = 'OK' if self.result is None else str(self.result)
        output = context_str + result_str
        if callable(self.verbose):
            return self.verbose(output)
        else:
            print (output)

    def apply(self, wake, executor, routine):
        def inner_wake(value):
            coroutines.Exit.apply(self, wake, executor, routine)
        res = self.log()
        if isinstance(res, coroutines.Suspend):
            res.apply(inner_wake, executor, routine)
        else:
            inner_wake(None)

class Configuration:
    class InterpolatingDict(dict):
        class InterpolationTemplate(string.Template):
            idpattern = r'[a-zA-Z_-][a-zA-Z0-9_-]*'

        def __init__(self, base, extra):
            dict.__init__(self, extra)
            self.base = base

        def __missing__(self, key):
            rawvalue = self.base[key]
            ret = self.InterpolationTemplate(rawvalue).substitute(self)
            self[key] = ret
            return ret

    @classmethod
    def validate_name(cls, name):
        if not name or '//' in name or name.startswith('/'):
            raise ConfigurationError('Invalid section name: %r' % name)

    @classmethod
    def split_name(cls, name):
        return name.split('/')

    def __init__(self, path=None):
        if path is None: path = DEFAULT_CONFFILE
        self.path = path
        self.parser = None
        self._raw_cache = None
        self._cache = None

    def load(self):
        self.parser = _ConfigParser()
        self.parser.read(self.path)
        for name in self.parser.sections():
            self.validate_name(name)
        self._raw_cache = {}
        self._cache = {}

    def list_sections(self):
        return [name for name in self.parser.sections()
                if not name.endswith('/')]

    def get_raw_section(self, name):
        if name in self._raw_cache: return self._raw_cache[name]
        self.validate_name(name)
        idx = name.rfind('/')
        if idx == -1:
            ret = {}
        elif idx == len(name) - 1:
            ret = dict(self.get_raw_section(name[:idx]))
        else:
            ret = dict(self.get_raw_section(name[:idx + 1]))
        try:
            pdata = dict(self.parser.items(name, raw=True))
        except _NoSectionError:
            pdata = {}
        impname = pdata.pop('__import__', None)
        if impname:
            ret.update(self.get_raw_section(impname))
        ret.update(pdata)
        self._raw_cache[name] = ret
        return ret

    def get_section(self, name):
        if name in self._cache: return self._cache[name]
        rawdata = self.get_raw_section(name)
        splname = self.split_name(name)
        interpolator = self.InterpolatingDict(rawdata,
            {'__fullname__': name, '__name__': splname[-1]})
        ret = {}
        for k in rawdata:
            v = interpolator[k]
            if k.startswith('__') and k.endswith('__'): continue
            ret[k] = v
        self._cache[name] = ret
        return ret

class Redirection:
    @classmethod
    def parse(cls, text):
        text = text.strip()
        try:
            constant = {'': None,
                        'stdout': subprocess.STDOUT,
                        'devnull': Ellipsis}[text]
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

    def open(self):
        if self.target is Ellipsis:
            return open(os.devnull, self.mode)
        elif isinstance(self.target, str):
            return open_mkdirs(self.target, self.mode, self.mkdirs)
        else:
            return self.target

    def close(self, fp):
        if fp is not self.target and hasattr(fp, 'close'):
            try:
                fp.close()
            except Exception:
                pass

class PIDFile:
    def __init__(self, path):
        self.path = path
        self._pid = Ellipsis

    def _read_file(self):
        f = None
        try:
            f = open(self.path)
            data = f.read()
            if not PID_LINE_RE.match(data):
                raise ValueError('Invalid PID file contents')
            ret = int(data)
            if ret <= 0:
                raise ValueError('Invalid PID in PID file')
            return ret
        except IOError as e:
            if e.errno == errno.ENOENT: return None
            raise
        finally:
            if f: f.close()

    def _write_file(self, pid):
        if pid is None:
            try:
                os.unlink(self.path)
            except OSError as e:
                if e.errno == errno.ENOENT: return
                raise
        else:
            with open_mkdirs(self.path, 'w') as f:
                f.write('%s\n' % pid)

    def get_pid(self, force=False):
        if self._pid is Ellipsis or force:
            self._pid = self._read_file()
        return self._pid

    def get_status(self, force_read=False):
        pid = self.get_pid(force=force_read)
        if pid is None:
            return (pid, 'NOT_RUNNING')
        try:
            os.kill(pid, 0)
            status = 'RUNNING'
        except OSError as e:
            if e.errno == errno.ESRCH:
                pid = None
                status = 'NOT_FOUND'
            elif e.errno == errno.EPERM:
                status = 'RUNNING_PRIVILEGED'
            else:
                raise
        return (pid, status)

    def set_pid(self, pid):
        self._pid = pid
        self._write_file(pid)

    def move_to(self, other):
        try:
            os.rename(self.path, other.path)
        except OSError as e:
            if e.errno != errno.ENOENT: raise
            other.set_pid(None)
        other._pid, self._pid = self._pid, Ellipsis

class BaseProcess:
    class DummyPopen:
        def __init__(self, pid):
            self.pid = pid
            self.returncode = None

        def poll(self):
            if self.returncode is None:
                pid, status = os.waitpid(self.pid, os.WNOHANG)
                if pid != 0:
                    code = -(status & 0x7F) if status & 0xFF else status >> 8
                    self.returncode = code
            return self.returncode

        def terminate(self):
            if self.returncode is None:
                os.kill(self.pid, signal.SIGTERM)

        def kill(self):
            if self.returncode is None:
                os.kill(self.pid, signal.SIGKILL)

    def __init__(self, name, pidfile, manager=None):
        self.name = name
        self.pidfile = pidfile
        self.manager = manager
        self._child = None

    def prepare_inherit(self):
        if self._child is not None:
            return (self._child.pid, self.pidfile.path)

    def restore_inherit(self, values):
        if values is None: return
        pid, pidpath = values
        self._child = self.DummyPopen(pid)
        # The PID file path might have changed; we allow that in here. The
        # value is used when constructing ProcessHusk instances.

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

    def init(self):
        def add_child(executor, routine):
            executor.waits.add(self._child)
        if self._child is not None:
            yield coroutines.CallbackSuspend(add_child)

    def warmup(self, wait=True, verbose=False):
        raise NotImplementedError

    def start(self, wait=True, verbose=False):
        raise NotImplementedError

    def stop(self, wait=True, verbose=False):
        raise NotImplementedError

    def status(self, verbose=False):
        exit = self._make_exit(None, verbose)
        if self._child is not None:
            code = self._child.poll()
            if code is not None:
                status = 'EXITED %s' % code
            else:
                status = 'RUNNING %s' % self._child.pid
        else:
            pid, status = self.pidfile.get_status()
            if pid is not None:
                status = '%s %s' % (status, pid)
        yield exit(status)

class Process(BaseProcess):
    def __init__(self, name, command, config, manager=None):
        BaseProcess.__init__(self, name, PIDFile(config.get('pid-file',
            DEFAULT_PIDFILE_TEMPLATE % name)), manager)
        self.command = command
        self.env = config.get('env', {})
        self.mkdirs = config.get('mkdirs', ())
        self.pidfile_next = PIDFile(config.get('pid-file-warmup',
            self.pidfile.path + WARMUP_PIDFILE_SUFFIX))
        self.workdir = config.get('work-dir')
        self.stdin = Redirection.parse(config.get('stdin', ''))
        self.stdout = Redirection.parse(config.get('stdout', ''))
        self.stderr = Redirection.parse(config.get('stderr', ''))
        self.stop_delay = float(config.get('stop-wait', 0))
        self.startup_notify = config.get('startup-notify')
        self.uuid = uuid.uuid4()
        self._next_child = None
        self._next_switcher = coroutines.StateSwitcher('')
        if manager:
            manager.register_notify(str(self.uuid), self._next_switcher)

    def _spawn_process(self, extra_args=None):
        for path in self.mkdirs:
            safe_makedirs(path)
        cmdline = self.command
        if extra_args: cmdline = tuple(cmdline) + tuple(extra_args)
        redirections = (self.stdin, self.stdout, self.stderr)
        files = []
        try:
            # NOTE: The finally clause relies on every file being added to
            #       files as soon as it is created -- [r.open() for r in
            #       redirections] may leak file descriptors.
            for r in redirections:
                files.append(r.open())
            final_env = dict(os.environ)
            final_env.update(self.env)
            proc = yield coroutines.SpawnProcess(args=cmdline, env=final_env,
                cwd=self.workdir, stdin=files[0], stdout=files[1],
                stderr=files[2])
            yield coroutines.Exit(proc)
        finally:
            for r, f in zip(redirections, files):
                r.close(f)

    def warmup(self, wait=True, verbose=False):
        exit = self._make_exit('warmup', verbose)
        if wait and (not self.startup_notify or ' ' in sys.executable or
                ' ' in THIS_FILE or not self.manager or
                not self.manager.has_notify):
            yield exit('NOT_SUPPORTED')
        pid, status = self.pidfile_next.get_status()
        if status == 'RUNNING' or self._next_child:
            yield exit('ALREADY_RUNNING')
        if wait:
            yield self._next_switcher.Set('STARTING')
            extra_args = (self.startup_notify, '%s %s cmd NOTIFY %s' % (
                sys.executable, THIS_FILE, self.uuid))
        else:
            extra_args = ()
        self._next_child = yield coroutines.Call(self._spawn_process(
            extra_args=extra_args))
        self.pidfile_next.set_pid(self._next_child.pid)
        if wait:
            yield self._next_switcher.Toggle('READY', 'STANDBY', True)
        yield exit('OK %s' % self._next_child.pid)

    def start(self, wait=True, verbose=False):
        exit = self._make_exit('start', verbose)
        pid, status = self.pidfile.get_status()
        if status == 'RUNNING':
            yield exit('ALREADY_RUNNING')
        if self._next_child:
            self._child, self._next_child = self._next_child, None
            self.pidfile_next.move_to(self.pidfile)
            yield self._next_switcher.Toggle('STANDBY', '')
            yield exit('OK %s' % self._child.pid)
        self._child = yield coroutines.Call(self._spawn_process())
        self.pidfile.set_pid(self._child.pid)
        yield exit('OK %s' % self._child.pid)

    def stop(self, wait=True, verbose=False):
        exit = self._make_exit('stop', verbose)
        prev_child = self._child
        status = 'OK'
        if self._child is not None:
            try:
                self._child.terminate()
            except OSError as e:
                if e.errno != errno.ESRCH: raise
            self._child = None
        else:
            # We could theoretically wait for the PID below, but that would be
            # even more fragile than what we already do in status().
            pid = self.pidfile.get_pid()
            if pid is None:
                yield exit('NOT_RUNNING')
            else:
                try:
                    os.kill(pid, signal.SIGTERM)
                except OSError as e:
                    if e.errno != errno.ESRCH: raise
                    status = 'NOT_FOUND'
        self.pidfile.set_pid(None)
        if wait:
            if prev_child:
                raw_status = yield coroutines.WaitProcess(prev_child)
                status = 'OK %s' % (raw_status,)
            elif status == 'OK':
                delay = self.stop_delay
                if delay > 0: yield coroutines.Sleep(delay)
                status = None
        yield exit(status)

class ProcessHusk(BaseProcess):
    def warmup(self, wait=True, verbose=False):
        exit = self._make_exit('warmup', verbose)
        yield exit('NOT_SUPPORTED')

    def start(self, wait=True, verbose=False):
        exit = self._make_exit('start', verbose)
        yield exit('NOT_SUPPORTED')

    def stop(self, wait=True, verbose=False):
        exit = self._make_exit('stop', verbose)
        if self._child is None:
            yield exit('NOT_RUNNING')
        try:
            self._child.terminate()
        except OSError as e:
            if e.errno != errno.ESRCH: raise
        self.pidfile.set_pid(None)
        if wait and self._child:
            raw_status = yield coroutines.WaitProcess(self._child)
            status = 'OK %s' % (raw_status,)
        else:
            status = 'OK'
        self._child = None
        yield exit(status)

class ProcessGroup:
    def __init__(self):
        self.processes = []

    def add(self, proc):
        self.processes.append(proc)

    def remove(self, procs):
        self.processes.remove(proc)

    def prepare_inherit(self):
        ret = {}
        for proc in self.processes:
            result = proc.prepare_inherit()
            if result is not None: ret[proc.name] = result
        if not ret: ret = None
        return ret

    def restore_inherit(self, values):
        if values is None: return
        procmap = {proc.name: proc for proc in self.processes}
        for name, value in values.items():
            if name in procmap:
                procmap[name].restore_inherit(value)
            elif isinstance(values, dict):
                pg = ProcessGroup()
                pg.restore_inherit(value)
                self.add(pg)
            else:
                proc = ProcessHusk(name, value[1])
                proc.restore_inherit(value)
                self.add(proc)

    def _for_each(self, procs, handler, prune=False):
        if procs is None:
            eff_procs = self.processes
        else:
            eff_procs = []
            procs = set(procs)
            for p in self.processes:
                if p in procs or p.name in procs:
                    eff_procs.append(p)
                    procs.discard(p)
                    procs.discard(p.name)
            if procs:
                raise NoSuchProcessesError(procs)
        calls = [coroutines.Call(handler(p)) for p in eff_procs]
        result = yield coroutines.All(*calls)
        if prune:
            self.processes[:] = [p for p in self.processes
                                 if not isinstance(p, ProcessHusk)]
        yield coroutines.Exit(result)

    def init(self, procs=None):
        return self._for_each(procs, lambda p: p.init())

    def warmup(self, wait=True, verbose=False, procs=None):
        return self._for_each(procs, lambda p: p.warmup(wait, verbose))

    def start(self, wait=True, verbose=False, procs=None):
        return self._for_each(procs, lambda p: p.start(wait, verbose))

    def stop(self, wait=True, verbose=False, procs=None):
        return self._for_each(procs, lambda p: p.stop(wait, verbose), True)

    def status(self, verbose=False, procs=None):
        return self._for_each(procs, lambda p: p.status(verbose))

OPERATIONS = {}
def operation(**params):
    def callback(func):
        if not func.__name__.startswith('do_'):
            raise ValueError('Unrecognized operation function name')
        opname = func.__name__[3:]
        argspec = inspect.getargspec(func)
        defaultlist = argspec.defaults or ()
        defaults = dict(zip(argspec.args[-len(defaultlist):], defaultlist))
        OPERATIONS[opname] = {'cb': func, 'doc': func.__doc__, 'types': types,
                              'params': params, 'defaults': defaults,
                              'index': len(OPERATIONS)}
        return func
    types = {k: v[0] for k, v in params.items()}
    return callback

class ProcessManager:
    @classmethod
    def parse_line(cls, line, types):
        data, positional_key = {}, None
        for word in line:
            key, sep, value = word.partition('=')
            if not sep:
                if positional_key is None:
                    try:
                        positional_key = find_dict_key(types, list)
                    except LookupError:
                        raise ValueError('Positional argument specified '
                            'although none are expected')
                    data[positional_key] = []
                data[positional_key].append(word)
                continue
            try:
                tp = types[key]
            except KeyError:
                raise ValueError('Named argument %r not declared' % (key,))
            if tp in (int, float):
                value = tp(value)
            elif tp == bool:
                value = is_true(value)
            elif tp == str:
                pass
            else:
                raise TypeError('Unrecognized type: %r' % (tp,))
            data[key] = value
        return data

    @classmethod
    def compose_line(cls, data, types):
        words, poswords = [], None
        for k, v in data.items():
            if k not in types:
                raise KeyError('Named argument %r not declared' % (k,))
            elif types[k] == list:
                if poswords is not None:
                    raise ValueError('Multiple sets of positional values '
                        'specified')
                elif any('=' in w for w in v):
                    raise ValueError('Positional values may not contain '
                        '\'=\' characters')
                poswords = v
            elif types[k] in (int, float, bool, str):
                words.append('%s=%s' % (k, v))
            else:
                raise TypeError('Unrecognized type: %r' % (types[k],))
        if not poswords: poswords = []
        return words + poswords

    def __init__(self, conffile, group=None):
        if group is None: group = ProcessGroup()
        self.conffile = conffile
        self.group = group
        self.has_notify = False
        self.notifies = {}

    def load(self):
        sections = [s for s in self.conffile.list_sections()
                    if self.conffile.split_name(s)[0] in PROCESS_SECTIONS]
        sections.sort()
        seen_names = set()
        for secname in sections:
            values = dict(self.conffile.get_section(secname))
            try:
                name = values['name']
            except KeyError:
                raise ConfigurationError('Missing required key "name" in '
                    'section %r' % secname)
            if '=' in name:
                raise ConfigurationError('Invalid process name in section '
                    '%r: %r contains equals sign (=)' % (secname, name))
            if name in seen_names:
                raise ConfigurationError('Duplicate process name %r in '
                    'section %r' % (name, secname))
            seen_names.add(name)
            try:
                cmdline = values['cmdline']
            except KeyError:
                raise ConfigurationError('Missing required key "cmdline" in '
                    'section %r' % secname)
            command = tuple(shlex.split(cmdline))
            raw_env = tuple(shlex.split(values.get('env', '')))
            values['env'] = {}
            for entry in raw_env:
                k, s, v = entry.partition('=')
                if not s:
                    raise ConfigurationError('Missing equals sign in '
                        'environment entry %r in section %r' % (entry,
                        secname))
                values['env'][k] = v
            values['mkdirs'] = tuple(shlex.split(values.get('mkdirs', '')))
            self.group.add(Process(name, command, values, self))

    def prepare_inherit(self):
        res = self.group.prepare_inherit()
        if res is None: return ''
        lines = []
        for name, value in res.items():
            if isinstance(value, dict):
                raise ValueError('Cannot serialize nested ProcessGroup '
                    'state')
            lines.append('%s=%s=%s\n' % (name, value[0], value[1]))
        return ''.join(lines)

    def restore_inherit(self, data):
        values = {}
        for line in data.split('\n'):
            if not line: continue
            parts = line.split('=', 2)
            if len(parts) != 3:
                raise ValueError('Invalid serialized state line: %r' % line)
            values[parts[0]] = (int(parts[1]), parts[2])
        self.group.restore_inherit(values)

    def register_notify(self, key, obj):
        self.notifies[key] = obj

    def get_notify(self, key):
        return self.notifies.get(key)

    def init(self):
        yield coroutines.Call(self.group.init())

    @operation(wait=(bool, 'Whether to wait for the start\'s completion'),
               verbose=(bool, 'Whether to output status reports'),
               procs=(list, 'The processes to start (default: all)'))
    def do_start(self, wait=True, procs=None, verbose=True):
        "Start the given processes"
        kwds = {'procs': procs, 'verbose': verbose}
        yield coroutines.Call(self.group.start(**kwds))

    @operation(wait=(bool, 'Whether to wait for the stop\'s completion'),
               verbose=(bool, 'Whether to output status reports'),
               procs=(list, 'The processes to stop (default: all)'))
    def do_stop(self, wait=True, procs=None, verbose=True):
        "Stop the given processes"
        kwds = {'procs': procs, 'verbose': verbose}
        yield coroutines.Call(self.group.stop(**kwds))

    @operation(verbose=(bool, 'Whether to output status reports'),
               procs=(list, 'The processes to restart (default: all)'))
    def do_restart(self, procs=None, verbose=True):
        "Restart the given processes"
        kwds = {'procs': procs, 'verbose': verbose}
        yield coroutines.Call(self.group.stop(**kwds))
        yield coroutines.Call(self.group.start(**kwds))

    @operation(verbose=(bool, 'Whether to output status reports'),
               procs=(list, 'The processes to restart (default: all)'))
    def do_bg_restart(self, procs=None, verbose=True):
        "Restart the given processes with pre-loading the new instances"
        kwds = {'procs': procs, 'verbose': verbose}
        yield coroutines.Call(self.group.warmup(**kwds))
        yield coroutines.Call(self.group.stop(**kwds))
        yield coroutines.Call(self.group.start(**kwds))

    @operation(verbose=(bool, 'Whether to output status reports'),
               procs=(list, 'The processes to query (default: all)'))
    def do_status(self, procs=None, verbose=True):
        "Query the status of the given processes"
        kwds = {'procs': procs, 'verbose': verbose}
        yield coroutines.Call(self.group.status(**kwds))

REMOTE_COMMANDS = {}
def command(name, minargs=0, maxargs=None):
    def callback(func):
        def wrapper(self, cmd, *args):
            if len(args) < minargs:
                return coroutines.const_raise(RemoteError('TFARGS', 'Too few '
                    'arguments for command %s' % (cmd,)))
            elif maxargs is not None and len(args) > maxargs:
                return coroutines.const_raise(RemoteError('TMARGS', 'Too '
                    'many arguments for command %s' % (cmd,)))
            return func(self, cmd, *args)
        REMOTE_COMMANDS[name] = wrapper
        return func
    return callback

def _wrap_operation(opname, desc):
    def handler(self, cmd, *args):
        def log_handler(text):
            self.parent.manager_logger.info(text)
            if orig_verbose:
                return self.WriteLine('<', text)
        method = getattr(self.parent.manager, 'do_' + opname)
        try:
            kwds = self.parent.manager.parse_line(args, desc['types'])
        except ValueError as exc:
            raise RemoteError('SYNTAX', 'Bad command usage: %s: %s' %
                              (exc.__class__.__name__, exc))
        orig_verbose = kwds.get('verbose')
        kwds['verbose'] = log_handler
        yield self.parent.lock.Acquire()
        self.parent.manager_logger.info('Doing ' + opname + '...')
        try:
            result = yield coroutines.Call(method(**kwds))
        except NoSuchProcessesError as exc:
            raise RemoteError('NXPROCS', str(exc))
        except RunnerError as exc:
            raise RemoteError('UNK', str(exc))
        else:
            yield coroutines.Exit('OK')
        finally:
            self.parent.manager_logger.info('Done.')
            self.parent.lock.release()
    return command(opname.upper().replace('_', '-'))(handler)

for _name, _desc in OPERATIONS.items(): _wrap_operation(_name, _desc)
del _name, _desc

@command('PING')
def command_ping(self, cmd, *args):
    yield coroutines.Exit(('PONG',) + args)

@command('NOTIFY', minargs=1, maxargs=1)
def command_notify(self, cmd, key):
    switcher = self.parent.manager.get_notify(key)
    if not switcher:
        raise RemoteError('NXNOTIFY', 'No such notification endpoint')
    res = yield switcher.Toggle('STARTING', 'READY')
    if not res:
        yield coroutines.Exit('FAIL')
    yield switcher.Wait('')
    yield coroutines.Exit('OK')

@command('STOP-MASTER', maxargs=0)
def command_shutdown(self, cmd):
    yield self.parent.Stop()
    yield coroutines.Exit('OK')

@command('RESTART-MASTER', maxargs=0)
def command_restart_daemon(self, cmd):
    yield self.parent.lock.Acquire()
    # Ignoring other coroutines from here on.
    self.parent.manager_logger.info('Restarting server!')
    mgr = self.parent.manager
    data = mgr.prepare_inherit()
    exec_end, fork_end = socket.socketpair()
    cmdline = (sys.executable, THIS_FILE, '--config', mgr.conffile.path,
               'run-master', '--close-fds',
               '--restore-fd', str(sys.stdout.fileno()))
    pid = os.fork()
    if pid == 0:
        # In the child, we close the master socket, push the data into the
        # "pipe", wait for the other side to confirm that it is up, report
        # that to our client, and exit.
        exec_end.close()
        self.server.sock.close()
        with fork_end.makefile('w') as f:
            f.write(data)
        fork_end.shutdown(socket.SHUT_WR)
        # Wait until the other side closes their end of the socket pair so
        # that main() can re-connect after the OK has been sent back.
        fork_end.recv(1)
        self.sock.sendall(self.parent.compose_line(('OK',)))
        os._exit(0)
    fork_end.close()
    os.dup2(exec_end.fileno(), sys.stdout.fileno())
    exec_end.close()
    os.execv(sys.executable, cmdline)
    raise RuntimeError('exec() returned?!')

class Remote:
    class Server:
        def __init__(self, parent, path):
            self.parent = parent
            self.path = path
            self.sock = None
            self._next_id = 1
            self.logger = logging.getLogger('server')

        def listen(self):
            self.sock = socket.socket(socket.AF_UNIX)
            try:
                self.sock.bind(self.path)
            except socket.error as e:
                if e.errno != errno.EADDRINUSE: raise
                # UNIX domain sockets are annoying...
                try:
                    self.sock.connect(self.path)
                except socket.error as e:
                    if e.errno != errno.ECONNREFUSED: raise
                else:
                    raise RunnerError('Communication socket is already bound')
                os.unlink(self.path)
                self.sock.bind(self.path)
            self.sock.listen(5)
            self.logger.info('Listening on %r' % (self.sock.getsockname(),))

        def accept(self):
            return self._create_handler(*self.sock.accept())

        def _create_handler(self, sock, addr):
            ret = self.parent.ClientHandler(self.parent, self.path, sock,
                                            self, id=self._next_id)
            self._next_id += 1
            return ret

        def close(self):
            self.logger.info('Closing')
            try:
                os.unlink(self.path)
            except IOError:
                pass
            try:
                self.sock.close()
            except IOError:
                pass
            self.parent._remove_selects(self.sock)
            self.sock = None

        def run(self):
            try:
                while 1:
                    suspend, result = yield coroutines.Any(
                        coroutines.AcceptSocket(self.sock),
                        self.parent.WaitStop())
                    if isinstance(suspend, coroutines.Listen):
                        break
                    conn, addr = result
                    handler = self._create_handler(conn, addr)
                    yield coroutines.Spawn(handler.run())
                    conn = addr = handler = None
            finally:
                self.close()

    class Connection:
        def __init__(self, parent, path, sock=None):
            self.parent = parent
            self.path = path
            self.sock = sock
            self.reader = None
            self.report_handler = None
            if self.sock is not None: self._make_files()

        def _make_files(self):
            self.reader = coroutines.BinaryLineReader(self.sock)

        def connect(self):
            self.sock = socket.socket(socket.AF_UNIX)
            self.sock.connect(self.path)
            self._make_files()

        def close(self):
            if self.sock is not None:
                try:
                    self.sock.shutdown(socket.SHUT_RDWR)
                except IOError:
                    pass
                try:
                    self.sock.close()
                except IOError:
                    pass
            self.parent._remove_selects(self.sock)
            self.sock = None

        def ReadLineRaw(self):
            return self.reader.ReadLine()

        def WriteLineRaw(self, data):
            return coroutines.WriteAll(self.sock, data)

        def ReadLine(self):
            return coroutines.WrapperSuspend(self.ReadLineRaw(),
                self.parent.parse_line)

        def WriteLine(self, *items):
            return self.WriteLineRaw(self.parent.compose_line(items))

        def do_command(self, *cmdline):
            yield self.WriteLine(*cmdline)
            while 1:
                result = yield self.ReadLine()
                if result and result[0] == '<':
                    if self.report_handler:
                        self.report_handler(result)
                    continue
                yield coroutines.Exit(result)

    class ClientHandler(Connection):
        def __init__(self, parent, path, sock, server=None, id=None):
            Remote.Connection.__init__(self, parent, path, sock)
            self.id = id
            self.server = server
            self.logger = logging.getLogger('client/%s' % ('???'
                if id is None else id))

        def run(self):
            try:
                while 1:
                    line = yield self.ReadLine()
                    if line is None: break
                    command = None if len(line) == 0 else line[0]
                    handler = self.dispatch(command)
                    try:
                        result = yield coroutines.Call(handler(self, command,
                                                               *line[1:]))
                    except RemoteError as exc:
                        result = ('ERROR', exc.code, exc.message)
                    except Exception as exc:
                        yield self.WriteLine('ERROR', 'INTERNAL',
                                              '(internal error)')
                        self.logger.error('%r -> Internal error: %r' % (line,
                                                                        exc))
                        self.close()
                        raise
                    if result is None:
                        result = ()
                    elif isinstance(result, str):
                        result = (result,)
                    if result and result[0] == 'ERROR':
                        comment = 'Error:'
                    else:
                        comment = 'OK,'
                    self.logger.info('Command %r -> %s %r' % (line, comment,
                                                              result))
                    yield self.WriteLine(*result)
            finally:
                self.close()

        def dispatch(self, command):
            def fallback(self, command, *args):
                yield RemoteError('NXCMD', 'No such command: %s' % (command,))
            cmd = REMOTE_COMMANDS.get(command, fallback)
            return cmd

    @classmethod
    def parse_line(cls, data):
        if not data: return None
        data, ret = data.decode('utf-8').rstrip('\n'), []
        idx, endidx, new_word = 0, len(data), False
        while idx < endidx:
            m = ESCAPE_PARSE_RE.search(data, idx)
            if not m:
                append = data[idx:]
                idx = endidx
                if not append: continue
            elif m.group() == ' ':
                append = data[idx:m.start()]
                new_word = True
                idx = m.end()
                if not append: continue
            else:
                try:
                    esc = ESCAPE_CHARS[m.group()]
                except KeyError:
                    raise ValueError('Invalid escape sequence: %s' %
                                     m.group())
                append = data[idx:m.start()] + esc
                idx = m.end()
            if not ret:
                ret.append([])
            ret[-1].append(append)
            if new_word:
                ret.append([])
                new_word = False
        return tuple(''.join(l) for l in ret)

    @classmethod
    def compose_line(cls, items, encode=True):
        res = ' '.join(i.replace('\\', '\\\\').replace(' ', '\\ ')
                       .replace('\n', '\\n') or '\\z' for i in items)
        if encode: res = res.encode('utf-8') + b'\n'
        return res

    def __init__(self, conffile, manager=None):
        self.conffile = conffile
        self.manager = manager
        self.config = self.conffile.get_section('master')
        self.path = self.config.get('comm-path', DEFAULT_COMM_PATH)
        self.manager_logger = logging.getLogger('manager')
        self.lock = coroutines.Lock()
        self.executor = None
        self._token = object()

    def prepare_executor(self):
        if self.executor is None:
            self.executor = coroutines.Executor()
            coroutines.set_sigpipe(self.executor)
        return self.executor

    def close_executor(self):
        if self.executor is None: return
        try:
            self.executor.close()
        finally:
            self.executor = None

    def _remove_selects(self, *files):
        if self.executor is None: return
        self.executor.remove_selects(*files)

    def run_routine(self, routine):
        ex = self.prepare_executor()
        ex.add(routine)
        ex.run()

    def Stop(self):
        return coroutines.Trigger(self._token)

    def WaitStop(self):
        return coroutines.Listen(self._token)

    def listen(self):
        res = self.Server(self, self.path)
        res.listen()
        return res

    def run_server(self, srv=None):
        if srv is None: srv = self.listen()
        self.run_routine(srv.run())

    def connect(self):
        res = self.Connection(self, self.path)
        res.connect()
        return res

def setup_logging(config):
    section = config.get_section('master')
    logfile = section.get('log-file') or None
    if logfile: safe_makedirs(os.path.dirname(logfile))
    loglevel = section.get('log-level', 'INFO')
    if loglevel.isdigit(): loglevel = int(loglevel)
    timestamps = is_true(section.get('log-timestamps', 'yes'))
    logging.basicConfig(format='[' + ('%(asctime)s ' if timestamps else '') +
        '%(name)s %(levelname)s] %(message)s', datefmt='%Y-%m-%d %H:%M:%S',
        level=loglevel, filename=logfile)

def run_master(mgr, setup=None, close_fds=False):
    def interrupt(signo, frame):
        def interrupt_agent():
            yield remote.Stop()
        if remote.closing or not remote.executor:
            raise KeyboardInterrupt
        else:
            remote.closing = True
            remote.executor.add(interrupt_agent())
    setup_logging(mgr.conffile)
    remote = Remote(mgr.conffile, mgr)
    remote.closing = False
    signal.signal(signal.SIGINT, interrupt)
    signal.signal(signal.SIGTERM, interrupt)
    if setup: setup()
    srv = remote.listen()
    pidpath = remote.config.get('pid-file')
    if pidpath:
        pidfile = PIDFile(pidpath)
        pidfile.set_pid(os.getpid())
    else:
        pidfile = None
    remote.prepare_executor().add(mgr.init())
    if close_fds:
        devnull_fd = os.open(os.devnull, os.O_RDWR)
        os.dup2(devnull_fd, sys.stdin.fileno())
        os.dup2(devnull_fd, sys.stdout.fileno())
        if remote.config.get('log-file'):
            os.dup2(devnull_fd, sys.stderr.fileno())
        os.close(devnull_fd)
    try:
        remote.run_server(srv)
    finally:
        if pidfile:
            # Check if another process has taken over the PID file.
            try:
                do_delete = (pidfile.get_pid(True) == os.getpid())
            except Exception:
                do_delete = False
            if do_delete:
                pidfile.set_pid(None)
        remote.close_executor()

def do_connect(mode, config, remote=None):
    # No connections if disabled.
    if mode == 'off': return None
    if remote is None: remote = Remote(config)
    # First, try to connect right away.
    try:
        return remote.connect()
    except socket.error as exc:
        if exc.errno != errno.ENOENT:
            raise
        elif mode == 'on':
            raise SystemExit('ERROR: Cannot connect to master process')
    # Otherwise, if selected, start a server and connect to it.
    if mode not in ('spawn', 'fg'): return None
    cmdline = (sys.executable, THIS_FILE, '--config',
        config.path, 'run-master', '--close-fds')
    # The only real difference between spawn and fg is which process
    # assumes which role.
    rfd, wfd = os.pipe()
    pid = os.fork()
    if (pid == 0) == (mode == 'spawn'):
        # Assume the master role in the child process iff spawn is
        # selected.
        os.close(rfd)
        os.dup2(wfd, sys.stdout.fileno())
        os.execv(sys.executable, cmdline)
        raise RuntimeError('exec() returned?!')
    # In the client process, wait until the master process is ready (or
    # dead), and try to connect again.
    os.close(wfd)
    os.read(rfd, 1)
    os.close(rfd)
    return remote.connect()

def run_client(conn, cmdline, close_conn=False):
    def report_handler(line):
        if line is None or len(line) <= 1:
            return
        elif line[0] == '<':
            line = line[1:]
        elif line[0] == 'ERROR' and len(line) == 3:
            raise RemoteError(line[1], line[2])
        print (' '.join(line))
    def command_wrapper():
        try:
            result = yield coroutines.Call(conn.do_command(*cmdline))
        except IOError as e:
            if e.errno not in (errno.EPIPE, errno.ECONNRESET):
                raise
            is_eof[0] = True
        else:
            report_handler(result)
    is_eof = [False]
    conn.report_handler = report_handler
    conn.parent.run_routine(command_wrapper())
    if close_conn: conn.close()
    return is_eof[0]

def main():
    def str_no_equals(s):
        if '=' in s:
            raise ValueError('Positional arguments must not contain \'=\' '
                'characters')
        return s
    def coroutines_error_handler(exc, source):
        if isinstance(exc, RemoteError):
            orig_message = re.sub(r'^\[[A-za-z0-9-]+\] ', '', str(exc))
            raise SystemExit('ERROR: ' + orig_message)
        elif isinstance(exc, RunnerError):
            raise SystemExit('ERROR: ' + str(exc))
    def try_call(func, *args, **kwds):
        try:
            return func(*args, **kwds)
        except RunnerError as exc:
            coroutines_error_handler(exc, None)
    def master_setup(mgr, fd):
        if fd is None:
            return
        if fd in (sys.stdin.fileno(), sys.stdout.fileno(),
                  sys.stderr.fileno()):
            eff_fd = os.dup(fd)
        else:
            eff_fd = fd
        with os.fdopen(eff_fd) as f:
            mgr.restore_inherit(f.read())
    def standalone_main(mgr, func, kwds):
        yield coroutines.Call(mgr.init())
        yield coroutines.Call(func(**kwds))
    # Parse command line.
    p = argparse.ArgumentParser(
        description='Manage a number of processes',
        epilog='The --master option can have the following values: off = '
            'never use daemon; all other options use a daemon when '
            'available: auto = without daemon, run command locally; spawn = '
            'without daemon, spawn one in the background; fg = without '
            'daemon, become the daemon; on = without daemon, report an '
            'error; restart = restart an already-running daemon or spawn a '
            'new one; stop = like auto, but shut the daemon down when done. '
            'The last two modes do not cooperate well with other commands '
            'running concurrently.')
    p.add_argument('--config', '-c', default=DEFAULT_CONFFILE,
                   help='Configuration file location (default %(default)s)')
    MASTER_CHOICES = ('off', 'auto', 'spawn', 'fg', 'on', 'restart', 'stop')
    p.add_argument('--master', '-m', choices=MASTER_CHOICES,
                   help='Whether to offload actual process management to a '
                       'background daemon (default taken from config file, '
                       'or "auto")')
    sp = p.add_subparsers(dest='cmd',
                          description='The action to perform (invoke with a '
                              '--help option to see usage details)')
    sp.required = True
    for name, desc in sorted(OPERATIONS.items(),
                             key=lambda item: item[1]['index']):
        cmdp = sp.add_parser(name.replace('_', '-'), help=desc['doc'])
        for name, (tp, doc) in sorted(desc['params'].items()):
            prefix = '--'
            if tp == bool:
                kwds = {'type': is_true, 'metavar': 'BOOL'}
            elif tp == list:
                prefix = ''
                kwds = {'type': str_no_equals, 'nargs': '*'}
            else:
                kwds = {'type': tp, 'metavar': tp.__name__.upper()}
            if name in desc['defaults']:
                kwds['default'] = desc['defaults'][name]
            cmdp.add_argument(prefix + name.replace('_', '-'), help=doc,
                              **kwds)
    p_master = sp.add_parser('run-master', help='Start a job manager server')
    p_master.add_argument('--close-fds', action='store_true',
                          help='Close standard input, standard output, and '
                              'standard error (unless there is no log file '
                              'specified) after creating the communication '
                              'socket')
    p_master.add_argument('--restore-fd', type=int, metavar='FD',
                          help='(internal) Load a state dump in some '
                              'internal format from the given file '
                              'descriptor')
    p_cmd = sp.add_parser('cmd',
                          help='Execute a command in a job manager server',
                          epilog='Some values of the --master option are '
                              'treated specially: "off" is an error; "auto" '
                              'is converted into "spawn"; "stop", '
                              'consequently, starts a daemon (if there is '
                              'none), executes the command, and tears the '
                              'daemon (back) down.')
    p_cmd.add_argument('cmdline', nargs='+', help='Command line to execute')
    arguments = p.parse_args()
    # Build and validate configuration.
    config = Configuration(arguments.config)
    config.load()
    mgr = ProcessManager(config)
    try:
        mgr.load()
    except ConfigurationError as exc:
        raise SystemExit('ERROR: Invalid configuration: ' + str(exc))
    # Change directory.
    workdir = config.get_section('master').get('work-dir')
    if workdir:
        os.chdir(os.path.join(os.path.dirname(config.path), workdir))
        config.path = os.path.relpath(config.path)
    # Skip the below setup when running a master process.
    if arguments.cmd == 'run-master':
        mgr.has_notify = True
        run_master(mgr, setup=lambda: master_setup(mgr, arguments.restore_fd),
                   close_fds=arguments.close_fds)
        return
    # In client, decide upon a master process mode and validate it.
    if arguments.master is None:
        arguments.master = config.get_section('master').get('mode') or 'auto'
        if arguments.master not in MASTER_CHOICES:
            raise SystemExit('ERROR: Invalid master process mode in '
                'configuration: %r' % (arguments.master,))
    restart_master = (arguments.master == 'restart')
    stop_master = (arguments.master == 'stop')
    if restart_master:
        arguments.master = 'spawn'
    elif stop_master:
        arguments.master = 'auto'
    if arguments.cmd == 'cmd':
        if arguments.master == 'off':
            raise SystemExit('ERROR: "--master=off" conflicts with "cmd"')
        elif arguments.master == 'auto':
            arguments.master = 'spawn'
    # Prepare operation arguments.
    kwds = dict(arguments.__dict__)
    for k in ('config', 'master', 'cmd'):
        del kwds[k]
    if arguments.cmd != 'cmd':
        opname = arguments.cmd.replace('-', '_')
        optypes = OPERATIONS[opname]['types']
        for k, v in tuple(kwds.items()):
            if optypes[k] == list and not v:
                del kwds[k]
    # Potentially connect to master process.
    conn = try_call(do_connect, arguments.master, config=config)
    # If not connected, perform operation locally. (The manipulations of
    # arguments.master above together with do_connect() ensure that conn is
    # never None if arguments.cmd is 'cmd'.)
    if conn is None:
        func = getattr(mgr, 'do_' + opname)
        try_call(coroutines.run, [standalone_main(mgr, func, kwds)],
                 sigpipe=True, on_error=coroutines_error_handler)
        return
    # Otherwise, submit command to master process, handling master restarting
    # and shutdown.
    conn.parent.prepare_executor().error_cb = coroutines_error_handler
    if restart_master:
        try_call(run_client, conn, ('RESTART-MASTER',))
        conn = try_call(do_connect, 'on', config=config, remote=conn.parent)
    if arguments.cmd == 'cmd':
        cmdline = arguments.cmdline
    else:
        cmdline = [arguments.cmd.upper()] + mgr.compose_line(kwds, optypes)
    try:
        if try_call(run_client, conn, cmdline):
            stop_master = False
    finally:
        if stop_master:
            try_call(run_client, conn, ('STOP-MASTER',))
        conn.close()
        conn.parent.close_executor()

if __name__ == '__main__': main()
