#!/usr/bin/env python3
# -*- coding: ascii -*-

# An init script for running Instant and a number of bots.

import os, re, time
import errno, signal
import socket
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

REDIRECTION_RE = re.compile(r'^[<>|&]+')
PID_LINE_RE = re.compile(r'^[0-9]+\s*$')

ESCAPE_PARSE_RE = re.compile(r' |\\.?')
ESCAPE_CHARS = {'\\\\': '\\', '\\ ': ' ', '\\n': '\n', '\\z': ''}

class RunnerError(Exception): pass

class ConfigurationError(RunnerError): pass

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
    def __init__(self, path=None):
        if path is None: path = DEFAULT_CONFFILE
        self.path = path
        self.parser = None

    def load(self):
        self.parser = _ConfigParser()
        self.parser.read(self.path)

    def list_sections(self):
        return self.parser.sections()

    def get_section(self, name):
        try:
            return dict(self.parser.items(name))
        except _NoSectionError:
            return {}

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
        if hasattr(fp, 'close'):
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

    def set_pid(self, pid):
        self._pid = pid
        self._write_file(pid)

class Process:
    def __init__(self, name, command, config=None):
        if config is None: config = {}
        self.name = name
        self.command = command
        self.pidfile = PIDFile(config.get('pidfile',
                                          DEFAULT_PIDFILE_TEMPLATE % name))
        self.workdir = config.get('workdir')
        self.stdin = Redirection.parse(config.get('stdin', ''))
        self.stdout = Redirection.parse(config.get('stdout', ''))
        self.min_stop = float(config.get('min-stop', 0))
        self.stderr = Redirection.parse(config.get('stderr', ''))
        self._child = None

    def _redirectors(self):
        return (self.stdin, self.stdout, self.stderr)

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
        cur_status = yield coroutines.Call(self.status(verbose=False))
        if cur_status == 'RUNNING':
            yield exit('ALREADY_RUNNING')
        files = []
        try:
            # NOTE: The finally clause relies on every file being added to
            #       files as soon as it is created -- [r.open() for r in
            #       self._redirectors()] may leak file descriptors.
            for r in self._redirectors():
                files.append(r.open())
            self._child = yield coroutines.SpawnProcess(args=self.command,
                cwd=self.workdir, stdin=files[0], stdout=files[1],
                stderr=files[2])
        finally:
            for r, f in zip(self._redirectors(), files):
                r.close(f)
        self.pidfile.set_pid(self._child.pid)
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
            pid = self.pidfile.get_pid()
            if pid is None:
                yield exit('NOT_RUNNING')
            else:
                os.kill(pid, signal.SIGTERM)
        self.pidfile.set_pid(None)
        if wait:
            stopping_since = time.time()
            if prev_child:
                raw_status = yield coroutines.WaitProcess(prev_child)
                status = 'OK %s' % (raw_status,)
            else:
                status = None
            delay = stopping_since + self.min_stop - time.time()
            if delay > 0: yield coroutines.Sleep(delay)
        else:
            status = 'OK'
        yield exit(status)

    def status(self, verbose=True):
        exit = self._make_exit(None, verbose)
        pid = self.pidfile.get_pid()
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

    def _for_each(self, selector, handler):
        calls = [coroutines.Call(handler(p))
                 for p in filter(selector, self.processes)]
        result = yield coroutines.All(*calls)
        yield coroutines.Exit(result)

    def start(self, wait=True, verbose=False, selector=None):
        return self._for_each(selector, lambda p: p.start(wait, verbose))

    def stop(self, wait=True, verbose=False, selector=None):
        return self._for_each(selector, lambda p: p.stop(wait, verbose))

    def status(self, verbose=True, selector=None):
        return self._for_each(selector, lambda p: p.status(verbose))

OPERATIONS = {}
def operation(**params):
    def callback(func):
        if not func.__name__.startswith('do_'):
            raise ValueError('Unrecognized operation function name')
        opname = func.__name__[3:]
        OPERATIONS[opname] = {'cb': func, 'doc': func.__doc__, 'types': types,
                              'params': params}
        return func
    types = {k: v[0] for k, v in params.items()}
    return callback

class InstantManager(ProcessGroup):
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

    def __init__(self, conffile):
        ProcessGroup.__init__(self)
        self.conffile = conffile

    def init(self):
        sections = [s for s in self.conffile.list_sections()
                    if s == 'instant' or s.startswith('scribe-')]
        sections.sort()
        for s in sections:
            values = self.conffile.get_section(s)
            try:
                cmdline = values['cmdline']
            except KeyError:
                raise ConfigurationError('Missing required key "cmdline" in '
                    'section "%s"' % s)
            command = tuple(shlex.split(cmdline))
            self.add(Process(s, command, values))

    def _process_selector(self, procs):
        if not procs: return None
        procs = frozenset(procs)
        return lambda p: p.name in procs or p in procs

    @operation(wait=(bool, 'Whether to wait for the start\'s completion'),
               procs=(list, 'The processes to start (default: all)'))
    def do_start(self, wait=True, procs=None, verbose=False):
        "Start the given processes"
        selector = self._process_selector(procs)
        yield coroutines.Call(self.start(verbose=verbose, selector=selector))

    @operation(wait=(bool, 'Whether to wait for the stop\'s completion'),
               procs=(list, 'The processes to stop (default: all)'))
    def do_stop(self, wait=True, procs=None, verbose=False):
        "Stop the given processes"
        selector = self._process_selector(procs)
        yield coroutines.Call(self.stop(verbose=verbose, selector=selector))

    @operation(procs=(list, 'The processes to restart (default: all)'))
    def do_restart(self, procs=None, verbose=False):
        "Restart the given processes"
        selector = self._process_selector(procs)
        yield coroutines.Call(self.stop(verbose=verbose, selector=selector))
        yield coroutines.Call(self.start(verbose=verbose, selector=selector))

    @operation(procs=(list, 'The processes to query (default: all)'))
    def do_status(self, procs=None, verbose=False):
        "Query the status of the given processes"
        selector = self._process_selector(procs)
        yield coroutines.Call(self.status(verbose=verbose, selector=selector))

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
            return self.WriteLine('<', text)
        method = getattr(self.parent.manager, 'do_' + opname)
        try:
            kwds = self.parent.manager.parse_line(args, desc['types'])
        except ValueError as exc:
            raise RemoteError('SYNTAX', 'Bad command usage: %s: %s' %
                              (exc.__class__.__name__, exc))
        kwds['verbose'] = log_handler
        self.parent.lock.Acquire()
        try:
            result = yield coroutines.Call(method(**kwds))
        except RunnerError as exc:
            raise RemoteError('UNK', str(exc))
        else:
            yield coroutines.Exit('OK')
        finally:
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
            self.rfile = None
            self.wfile = None
            self.reader = None
            self.report_handler = None
            if self.sock is not None: self._make_files()

        def _make_files(self):
            # FIXME: Py2K does not perform partial reads on socket files even
            #        if unbuffered mode is requested; this hangs coroutines'
            #        main loop.
            self.rfile = self.sock.makefile('rb', 0)
            self.wfile = self.sock.makefile('wb', 0)
            self.reader = coroutines.BinaryLineReader(self.rfile)

        def connect(self):
            self.sock = socket.socket(socket.AF_UNIX)
            self.sock.connect(self.path)
            self._make_files()

        def close(self):
            try:
                if self.sock is not None:
                    self.sock.shutdown(socket.SHUT_RDWR)
            except IOError:
                pass
            for item in (self.rfile, self.wfile, self.sock):
                try:
                    if item is not None:
                        item.close()
                except IOError:
                    pass
            self.parent._remove_selects(self.rfile, self.wfile, self.sock)
            self.rfile = self.wfile = self.sock = None

        def ReadLineRaw(self):
            return self.reader.ReadLine()

        def WriteLineRaw(self, data):
            return coroutines.WriteAll(self.wfile, data)

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
        self.lock = coroutines.Lock()
        self.executor = None
        self._token = object()

    def _prepare_executor(self):
        if self.executor is None:
            self.executor = coroutines.Executor()
            coroutines.set_sigpipe(self.executor)
        return self.executor

    def _close_executor(self):
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
            ex = self._prepare_executor()
            ex.add(routine)
            ex.run()
        finally:
            self._close_executor()

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

def setup_logging(config, timestamps=None):
    section = config.get_section('master')
    if timestamps is None:
        timestamps = is_true(section.get('log-timestamps', ''))
    logfile = section.get('logfile') or None
    loglevel = section.get('loglevel', 'INFO')
    if loglevel.isdigit(): loglevel = int(loglevel)
    logging.basicConfig(format='[' + ('%(asctime)s ' if timestamps else '') +
        '%(name)s %(levelname)s] %(message)s', datefmt='%Y-%m-%d %H:%M:%S',
        level=loglevel, filename=logfile)

def main():
    def str_no_equals(s):
        if '=' in s:
            raise ValueError('Positional arguments must not contain \'=\' '
                'characters')
        return s
    def interrupt(remote):
        def interrupt_agent():
            yield remote.Stop()
        if remote.closing or not remote.executor:
            raise KeyboardInterrupt
        else:
            remote.closing = True
            remote.executor.add(interrupt_agent())
    def report_handler(line):
        if line is None or len(line) < 2: return
        print (' '.join(line[1:]))
    def command_wrapper(remote, conn, cmdline):
        result = yield coroutines.Call(conn.do_command(*cmdline))
        report_handler(result)
    p = argparse.ArgumentParser(
        description='Manage an Instant backend and a group of bots')
    p.add_argument('--config', '-c', default=DEFAULT_CONFFILE,
                   help='Configuration file location (default %(default)s)')
    sp = p.add_subparsers(dest='cmd',
                          description='The action to perform (invoke with a '
                              '--help option to see usage details)')
    sp.required = True
    p_master = sp.add_parser('run-master', help='Start a job manager server')
    p_master.add_argument('--no-log-timestamps', action='store_false',
                          dest='log_timestamps',
                          help='Do not add timestamps to log messages')
    p_cmd = sp.add_parser('cmd',
                          help='Execute a command in a job manager server')
    p_cmd.add_argument('cmdline', nargs='+', help='Command line to execute')
    for name, desc in sorted(OPERATIONS.items()):
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
            cmdp.add_argument(prefix + name.replace('_', '-'), help=doc,
                              **kwds)
    arguments = p.parse_args()
    config = Configuration(arguments.config)
    config.load()
    mgr = InstantManager(config)
    try:
        mgr.init()
    except ConfigurationError as exc:
        raise SystemExit('Configuration error: ' + str(exc))
    if arguments.cmd == 'run-master':
        setup_logging(config, arguments.log_timestamps)
        remote = Remote(config, mgr)
        remote.closing = False
        signal.signal(signal.SIGINT, lambda sn, f: interrupt(remote))
        signal.signal(signal.SIGTERM, lambda sn, f: interrupt(remote))
        remote.run_server()
        return
    elif arguments.cmd == 'cmd':
        remote = Remote(config)
        conn = remote.connect()
        conn.report_handler = report_handler
        remote.run_routine(command_wrapper(remote, conn, arguments.cmdline))
        return
    kwds = dict(arguments.__dict__)
    for k in ('config', 'cmd'): del kwds[k]
    kwds['verbose'] = True
    func = getattr(mgr, 'do_' + arguments.cmd.replace('-', '_'))
    coroutines.run([func(**kwds)], sigpipe=True)

if __name__ == '__main__': main()
