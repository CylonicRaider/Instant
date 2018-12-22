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
    class _InterpolatingDict(dict):
        def __init__(self, base, extra):
            dict.__init__(self, extra)
            self.base = base

        def __missing__(self, key):
            rawvalue = self.base[key]
            ret = string.Template(rawvalue).substitute(self)
            self[key] = ret
            return ret

    @classmethod
    def validate_name(cls, name):
        if not name or '//' in name or name.startswith('/'):
            raise ConfigurationError('Invalid section name: %r' % name)

    @classmethod
    def split_name(cls, name):
        return name.strip('/').split('/')

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
        if name in self._raw_cache:
            return self._raw_cache[name]
        self.validate_name(name)
        idx = name.rfind('/')
        if idx == -1:
            ret = {}
        elif idx == len(name) - 1:
            ret = dict(self.get_raw_section(name[:idx]))
        else:
            ret = dict(self.get_raw_section(name[:idx + 1]))
        try:
            ret.update(self.parser.items(name, raw=True))
        except _NoSectionError:
            pass
        self._raw_cache[name] = ret
        return ret

    def get_section(self, name):
        if name in self._cache: return self._cache[name]
        rawdata = self.get_raw_section(name)
        splname = self.split_name(name)
        interpolator = self._InterpolatingDict(rawdata,
            {'__fullname__': name, '__name__': splname[-1]})
        ret = {}
        # Manually copying entries because _InterpolatingDict does not
        # redefine key enumeration.
        for k in rawdata: ret[k] = interpolator[k]
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
                status = 'STALEFILE'
            elif e.errno == errno.EPERM:
                status = 'RUNNING_PRIVILEGED'
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

class Process:
    def __init__(self, name, command, config, manager=None):
        self.name = name
        self.command = command
        self.manager = manager
        self.pidfile = PIDFile(config.get('pid-file',
            DEFAULT_PIDFILE_TEMPLATE % name))
        self.pidfile_next = PIDFile(config.get('pid-file-warmup',
            self.pidfile.path + WARMUP_PIDFILE_SUFFIX))
        self.workdir = config.get('work-dir')
        self.stdin = Redirection.parse(config.get('stdin', ''))
        self.stdout = Redirection.parse(config.get('stdout', ''))
        self.stderr = Redirection.parse(config.get('stderr', ''))
        self.stop_delay = float(config.get('stop-wait', 0))
        self.startup_notify = config.get('startup-notify')
        self.uuid = uuid.uuid4()
        self._child = None
        self._next_child = None
        self._next_switcher = coroutines.StateSwitcher('')
        if manager:
            manager.register_notify(str(self.uuid), self._next_switcher)

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

    def _spawn_process(self, extra_args=None):
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
            proc = yield coroutines.SpawnProcess(args=cmdline,
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
        if self._child is not None:
            self._child.terminate()
            self._child = None
        else:
            # We could theoretically wait for the PID below, but that would be
            # even more fragile than what we already do in status().
            pid = self.pidfile.get_pid()
            if pid is None:
                yield exit('NOT_RUNNING')
            else:
                os.kill(pid, signal.SIGTERM)
        self.pidfile.set_pid(None)
        if wait:
            if prev_child:
                raw_status = yield coroutines.WaitProcess(prev_child)
                status = 'OK %s' % (raw_status,)
            else:
                delay = self.stop_delay
                if delay > 0: yield coroutines.Sleep(delay)
                status = None
        else:
            status = 'OK'
        yield exit(status)

    def status(self, verbose=False):
        exit = self._make_exit(None, verbose)
        pid, status = self.pidfile.get_status()
        if pid is not None: status = '%s %s' % (status, pid)
        yield exit(status)

class ProcessGroup:
    def __init__(self):
        self.processes = []

    def add(self, proc):
        self.processes.append(proc)

    def _for_each(self, procs, handler):
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
        yield coroutines.Exit(result)

    def warmup(self, wait=True, verbose=False, procs=None):
        return self._for_each(procs, lambda p: p.warmup(wait, verbose))

    def start(self, wait=True, verbose=False, procs=None):
        return self._for_each(procs, lambda p: p.start(wait, verbose))

    def stop(self, wait=True, verbose=False, procs=None):
        return self._for_each(procs, lambda p: p.stop(wait, verbose))

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

    def init(self):
        sections = [s for s in self.conffile.list_sections()
                    if self.conffile.split_name(s)[0] in PROCESS_SECTIONS]
        sections.sort()
        for s in sections:
            values = self.conffile.get_section(s)
            try:
                name = values['name']
            except KeyError:
                raise ConfigurationError('Missing required key "name" in '
                    'section %r' % s)
            try:
                cmdline = values['cmdline']
            except KeyError:
                raise ConfigurationError('Missing required key "cmdline" in '
                    'section %r' % s)
            command = tuple(shlex.split(cmdline))
            self.group.add(Process(name, command, values, self))

    def register_notify(self, key, obj):
        self.notifies[key] = obj

    def get_notify(self, key):
        return self.notifies.get(key)

    @operation(wait=(bool, 'Whether to wait for the start\'s completion'),
               procs=(list, 'The processes to start (default: all)'))
    def do_start(self, wait=True, procs=None, verbose=False):
        "Start the given processes"
        kwds = {'procs': procs, 'verbose': verbose}
        yield coroutines.Call(self.group.start(**kwds))

    @operation(wait=(bool, 'Whether to wait for the stop\'s completion'),
               procs=(list, 'The processes to stop (default: all)'))
    def do_stop(self, wait=True, procs=None, verbose=False):
        "Stop the given processes"
        kwds = {'procs': procs, 'verbose': verbose}
        yield coroutines.Call(self.group.stop(**kwds))

    @operation(procs=(list, 'The processes to restart (default: all)'))
    def do_restart(self, procs=None, verbose=False):
        "Restart the given processes"
        kwds = {'procs': procs, 'verbose': verbose}
        yield coroutines.Call(self.group.stop(**kwds))
        yield coroutines.Call(self.group.start(**kwds))

    @operation(procs=(list, 'The processes to restart (default: all)'))
    def do_bg_restart(self, procs=None, verbose=False):
        "Restart the given processes with pre-loading the new instances"
        kwds = {'procs': procs, 'verbose': verbose}
        yield coroutines.Call(self.group.warmup(**kwds))
        yield coroutines.Call(self.group.stop(**kwds))
        yield coroutines.Call(self.group.start(**kwds))

    @operation(procs=(list, 'The processes to query (default: all)'))
    def do_status(self, procs=None, verbose=False):
        "Query the status of the given processes"
        kwds = {'procs': procs, 'verbose': verbose}
        yield coroutines.Call(self.group.status(**kwds))

REMOTE_COMMANDS = {}
def command(name, minargs=0, maxargs=None):
    def callback(func):
        def wrapper(self, cmd, *args):
            if len(args) < minargs:
                return coroutines.constRaise(RemoteError('TFARGS', 'Too few '
                    'arguments for command %s' % (cmd,)))
            elif maxargs is not None and len(args) > maxargs:
                return coroutines.constRaise(RemoteError('TMARGS', 'Too many '
                    'arguments for command %s' % (cmd,)))
            return func(self, cmd, *args)
        REMOTE_COMMANDS[name] = wrapper
        return func
    return callback

def _wrap_operation(opname, desc):
    def handler(self, cmd, *args):
        def log_handler(text):
            self.parent.manager_logger.info(text)
            return self.WriteLine('<', text)
        method = getattr(self.parent.manager, 'do_' + opname)
        try:
            kwds = self.parent.manager.parse_line(args, desc['types'])
        except ValueError as exc:
            raise RemoteError('SYNTAX', 'Bad command usage: %s: %s' %
                              (exc.__class__.__name__, exc))
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

@command('SHUTDOWN', maxargs=0)
def command_shutdown(self, cmd):
    yield self.parent.Stop()
    yield coroutines.Exit('OK')

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
                                            id=self._next_id)
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
            # FIXME: Py2K does not perform partial reads on socket files even
            #        if unbuffered mode is requested; this hangs coroutines'
            #        main loop.
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
        def __init__(self, parent, path, sock, id=None):
            Remote.Connection.__init__(self, parent, path, sock)
            self.id = id
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
        self.path = self.config.get('path', DEFAULT_COMM_PATH)
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
        try:
            ex = self.prepare_executor()
            ex.add(routine)
            ex.run()
        finally:
            self.close_executor()

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
    loglevel = section.get('log-level', 'INFO')
    if loglevel.isdigit(): loglevel = int(loglevel)
    timestamps = is_true(section.get('log-timestamps', 'yes'))
    logging.basicConfig(format='[' + ('%(asctime)s ' if timestamps else '') +
        '%(name)s %(levelname)s] %(message)s', datefmt='%Y-%m-%d %H:%M:%S',
        level=loglevel, filename=logfile)

def run_master(mgr, close_fds=False):
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
    srv = remote.listen()
    if close_fds:
        devnull_fd = os.open(os.devnull, os.O_RDWR)
        os.dup2(devnull_fd, sys.stdin.fileno())
        os.dup2(devnull_fd, sys.stdout.fileno())
        if remote.config.get('log-file'):
            os.dup2(devnull_fd, sys.stderr.fileno())
        os.close(devnull_fd)
    pidpath = remote.config.get('pid-file')
    if pidpath:
        pidfile = PIDFile(pidpath)
        pidfile.set_pid(os.getpid())
    else:
        pidfile = None
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
        result = yield coroutines.Call(conn.do_command(*cmdline))
        report_handler(result)
    conn.report_handler = report_handler
    conn.parent.run_routine(command_wrapper())
    if close_conn: conn.close()

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
    def do_connect(mode, conffile_path):
        # No connections if disabled.
        if mode == 'off': return None
        remote = Remote(config)
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
            conffile_path, 'run-master', '--close-fds')
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
    p = argparse.ArgumentParser(
        description='Manage an Instant backend and a group of bots',
        epilog='The --master option can have the following values: off = '
            'never use daemon; all remaining options use a daemon when '
            'available: auto = without daemon, run command locally; spawn = '
            'without daemon, spawn one in the background; fg = without '
            'daemon, become the daemon; on = without daemon, report an '
            'error; stop = equivalent to auto, but shuts the daemon down '
            'when done.')
    p.add_argument('--config', '-c', default=DEFAULT_CONFFILE,
                   help='Configuration file location (default %(default)s)')
    p.add_argument('--master', '-m', default='auto', choices=('off', 'auto',
                       'spawn', 'fg', 'on', 'stop'),
                   help='Whether to offload actual process management to a '
                       'background daemon (defaults to auto)')
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
    config = Configuration(arguments.config)
    config.load()
    mgr = ProcessManager(config)
    try:
        mgr.init()
    except ConfigurationError as exc:
        raise SystemExit('Configuration error: ' + str(exc))
    if arguments.cmd == 'run-master':
        mgr.has_notify = True
        run_master(mgr, close_fds=arguments.close_fds)
        return
    stop_master = (arguments.master == 'stop')
    if stop_master:
        arguments.master = 'auto'
    if arguments.cmd == 'cmd':
        if arguments.master == 'off':
            raise SystemExit('ERROR: "--master=off" conflicts with "cmd"')
        elif arguments.master == 'auto':
            arguments.master = 'spawn'
    conn = try_call(do_connect, arguments.master,
                    conffile_path=arguments.config)
    kwds = dict(arguments.__dict__)
    for k in ('config', 'master', 'cmd'):
        del kwds[k]
    if arguments.cmd != 'cmd':
        opname = arguments.cmd.replace('-', '_')
        optypes = OPERATIONS[opname]['types']
        for k, v in tuple(kwds.items()):
            if optypes[k] == list and not v:
                del kwds[k]
    if conn is not None:
        conn.parent.prepare_executor().error_cb = coroutines_error_handler
        if arguments.cmd == 'cmd':
            cmdline = arguments.cmdline
        else:
            cmdline = ([arguments.cmd.upper()] +
                       mgr.compose_line(kwds, optypes))
        try:
            try_call(run_client, conn, cmdline)
        finally:
            if stop_master: try_call(run_client, conn, ('SHUTDOWN',))
            conn.close()
    else:
        kwds['verbose'] = True
        func = getattr(mgr, 'do_' + opname)
        try_call(coroutines.run, [func(**kwds)], sigpipe=True,
                 on_error=coroutines_error_handler)

if __name__ == '__main__': main()
