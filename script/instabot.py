# -*- coding: ascii -*-

import sys, os, re, time, stat
import traceback
import collections, heapq, ast
import json
import socket
import threading

import websocket_server

try:
    from queue import Queue, Empty as QueueEmpty
except ImportError:
    from Queue import Queue, Empty as QueueEmpty

VERSION = 'v1.4.2'

RELAXED_COOKIES = bool(os.environ.get('INSTABOT_RELAXED_COOKIES'))

_unicode = websocket_server.compat.unicode

class EventScheduler(object):
    class Event:
        def __init__(self, time, seq, callback):
            self.time = time
            self.sortkey = (time, seq)
            self.callback = callback
            self.handled = False
            self.canceled = False
        def __call__(self):
            self.callback()
        def __gt__(self, other):
            return self.sortkey > other.sortkey
        def __ge__(self, other):
            return self.sortkey >= other.sortkey
        def __eq__(self, other):
            return self.sortkey == other.sortkey
        def __ne__(self, other):
            return self.sortkey != other.sortkey
        def __le__(self, other):
            return self.sortkey <= other.sortkey
        def __lt__(self, other):
            return self.sortkey < other.sortkey
    def __init__(self, time=None, sleep=None):
        if time is None: time = self._time
        if sleep is None: sleep = self._sleep
        self.pending = []
        self.time = time
        self.sleep = sleep
        self.forever = True
        self.cond = threading.Condition()
        self._seq = 0
    def __enter__(self):
        return self.cond.__enter__()
    def __exit__(self, *args):
        return self.cond.__exit__(*args)
    def _time(self):
        return time.time()
    def _sleep(self, delay):
        with self:
            self.cond.wait(delay)
            return bool(self.pending)
    def add_abs(self, timestamp, callback):
        with self:
            evt = self.Event(timestamp, self._seq, callback)
            self._seq += 1
            heapq.heappush(self.pending, evt)
            self.cond.notify()
            return evt
    def add(self, delay, callback):
        return self.add_abs(self.time() + delay, callback)
    def add_now(self, callback):
        return self.add_abs(self.time(), callback)
    def cancel(self, event):
        with self:
            event.canceled = True
            ret = (not event.handled)
            self.cond.notify()
            return ret
    def clear(self):
        with self:
            self.pending[:] = []
            self.cond.notify()
    def set_forever(self, v):
        with self:
            self.forever = v
            self.cond.notify()
    def run(self, hangup=True):
        wait = None
        while 1:
            with self:
                if not self.pending: break
                now = self.time()
                head = self.pending[0]
                if head.time > now and not head.canceled:
                    wait = head.time - now
                    break
                heapq.heappop(self.pending)
                head.handled = True
                if head.canceled: continue
            head()
        if wait is None and not hangup: return False
        return self.sleep(wait)
    def main(self):
        while 1:
            f = self.forever
            if not self.run(f) and not f: break

class AtomicSequence(object):
    def __init__(self):
        self.value = -1
        self._lock = threading.Lock()
    def __call__(self):
        with self._lock:
            self.value += 1
            return self.value

class InstantClient(object):
    TIMEOUT = None
    COOKIES = None
    def __init__(self, url, **kwds):
        self.url = url
        self.timeout = kwds.get('timeout', self.TIMEOUT)
        self.cookies = kwds.get('cookies', self.COOKIES)
        self.ws = None
        self.sequence = AtomicSequence()
        self._wslock = threading.RLock()
    def connect(self):
        with self._wslock:
            if self.ws is not None: return
            jar = self.cookies
            self.ws = websocket_server.client.connect(self.url,
                cookies=jar, timeout=self.timeout)
            if isinstance(jar, websocket_server.cookies.FileCookieJar):
                jar.save()
    def on_open(self):
        pass
    def on_message(self, rawmsg):
        content = json.loads(rawmsg)
        msgt = content.get('type')
        func = {
            'identity': self.handle_identity, 'pong': self.handle_pong,
            'joined': self.handle_joined, 'unicast': self.handle_unicast,
            'broadcast': self.handle_broadcast, 'reply': self.handle_reply,
            'left': self.handle_left, 'error': self.handle_error
        }.get(msgt, self.on_unknown)
        func(content, rawmsg)
    def on_frame(self, msgtype, content, final):
        pass
    def on_timeout(self, exc):
        raise exc
    def on_error(self, exc):
        raise exc
    def on_close(self):
        pass
    def handle_identity(self, content, rawmsg):
        pass
    def handle_pong(self, content, rawmsg):
        pass
    def handle_joined(self, content, rawmsg):
        pass
    def handle_unicast(self, content, rawmsg):
        self.on_client_message(content['data'], content, rawmsg)
    def handle_broadcast(self, content, rawmsg):
        self.on_client_message(content['data'], content, rawmsg)
    def handle_reply(self, content, rawmsg):
        pass
    def handle_left(self, content, rawmsg):
        pass
    def handle_error(self, content, rawmsg):
        pass
    def on_unknown(self, content, rawmsg):
        pass
    def on_client_message(self, data, content, rawmsg):
        pass
    def recv(self):
        ws = self.ws
        if ws is None: return None
        while 1:
            frame = ws.read_frame()
            if frame is None: return None
            if frame.msgtype != websocket_server.OP_TEXT:
                self.on_frame(frame.msgtype, frame.content, frame.final)
                continue
            return frame.content
    def send_raw(self, rawmsg):
        ws = self.ws
        if ws is None: raise websocket_server.ConnectionClosedError
        ws.write_text_frame(_unicode(rawmsg))
    def send_seq(self, content, **kwds):
        seq = self.sequence()
        content['seq'] = seq
        self.send_raw(json.dumps(content, separators=(',', ':')), **kwds)
        return seq
    def send_unicast(self, dest, data, **kwds):
        return self.send_seq({'type': 'unicast', 'to': dest, 'data': data},
                             **kwds)
    def send_broadcast(self, data, **kwds):
        return self.send_seq({'type': 'broadcast', 'data': data}, **kwds)
    def close(self):
        with self._wslock:
            if self.ws is not None: self.ws.close()
            self.ws = None
    def run(self):
        try:
            self.connect()
            self.on_open()
            while 1:
                try:
                    rawmsg = self.recv()
                except socket.timeout as exc:
                    self.on_timeout(exc)
                    continue
                if rawmsg is None:
                    break
                elif not rawmsg:
                    continue
                self.on_message(rawmsg)
        except websocket_server.ConnectionClosedError:
            # Server-side timeouts cause the connection to be dropped.
            pass
        except Exception as exc:
            self.on_error(exc)
        finally:
            try:
                self.close()
            except Exception as exc:
                self.on_error(exc)
            self.on_close()
    def start(self):
        thr = threading.Thread(target=self.run)
        thr.setDaemon(True)
        thr.start()
        return thr

class Bot(InstantClient):
    NICKNAME = None
    def __init__(self, url, nickname=None, **kwds):
        if nickname is None: nickname = self.NICKNAME
        InstantClient.__init__(self, url, **kwds)
        self.nickname = nickname
        self.identity = None
    def on_timeout(self, exc):
        if self.timeout is not None:
            self.send_seq({'type': 'ping'})
        else:
            raise exc
    def handle_identity(self, content, rawmsg):
        self.identity = content['data']
        if self.nickname is not None:
            self.send_broadcast({'type': 'nick', 'nick': self.nickname,
                                 'uuid': self.identity['uuid']})
    def on_client_message(self, data, content, rawmsg):
        peer = content['from']
        if (data.get('type') == 'who' and peer != self.identity['id'] and
                self.nickname is not None):
            self.send_unicast(peer, {'type': 'nick', 'nick': self.nickname,
                                     'uuid': self.identity['uuid']})

class HookBot(Bot):
    def __init__(self, url, nickname=None, **kwds):
        Bot.__init__(self, url, nickname, **kwds)
        self.init_cb = kwds.get('init_cb')
        self.open_cb = kwds.get('open_cb')
        self.post_cb = kwds.get('post_cb')
        self.close_cb = kwds.get('close_cb')
        if self.init_cb is not None: self.init_cb(self)
    def on_open(self):
        Bot.on_open(self)
        if self.open_cb is not None: self.open_cb(self)
    def on_client_message(self, data, content, rawmsg):
        Bot.on_client_message(self, data, content, rawmsg)
        if data.get('type') == 'post':
            post = dict(data, timestamp=content['timestamp'],
                id=content['id'], **{'from': content['from']})
            if self.post_cb is not None:
                res = self.post_cb(self, post, {'content': content,
                                                'rawmsg': rawmsg})
                if res is not None:
                    self.send_post(res, content['id'])
    def on_close(self):
        Bot.on_close(self)
        if self.close_cb is not None: self.close_cb(self)
    def send_post(self, text, parent=None, nickname=None):
        data = {'type': 'post', 'text': text}
        if parent is not None: data['parent'] = parent
        if nickname is None:
            data['nick'] = self.nickname
        else:
            data['nick'] = nickname
        return self.send_broadcast(data)

def format_log(o):
    if isinstance(o, tuple):
        return '(' + ','.join(map(repr, o)) + ')'
    else:
        return repr(o)
def log(msg):
    m = '[%s] %s\n' % (time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime()),
                       msg)
    sys.stdout.write(m.encode('ascii', 'backslashreplace').decode('ascii'))
    sys.stdout.flush()
def log_exception(name, exc, trailer=None):
    try:
        # frame is the frame where the exception is caught; cause is the
        # frame where it originated. The former might be more useful in that
        # it points into the user's code (instead of nested libraries).
        frame = tuple(traceback.extract_tb(sys.exc_info()[2], 1)[-1])
        cause = tuple(traceback.extract_tb(sys.exc_info()[2])[-1])
    except:
        frame, cause = None, None
    # The exception is repr()-ed twice, since many of those objects have
    # custom representations, which are not necessarily machine-readable,
    # and str() is hardly appropriate.
    if frame == cause:
        msg = '%s reason=%r last-frame=%s' % (name, repr(exc),
                                              format_log(frame))
    else:
        msg = '%s reason=%r last-frame=%s cause-frame=%s' % (name, repr(exc),
            format_log(frame), format_log(cause))
    if trailer is not None: msg += ' ' + trailer
    log(msg)

LOGLINE_START = re.compile(r'^\[([0-9 Z:-]+)\]\s+([A-Z_-]+)\s+(.*)$')
WHITESPACE = re.compile(r'\s+')
SCALAR = re.compile(r'[^"\'\x28,\s]\S*|u?"(?:[^"\\]|\\.)*"|'
    r'u?\'(?:[^\'\\]|\\.)*\'')
TUPLE = re.compile(r'\(\s*(?:(?:%s)\s*,\s*)*(?:(?:%s)\s*)?\)' %
                   (SCALAR.pattern, SCALAR.pattern))
EMPTY_TUPLE = re.compile(r'^\(\s*\)$')
TRAILING_COMMA = re.compile(r',\s*\)$')
PARAM = re.compile(r'([a-zA-Z0-9_-]+)=(%s|%s)(?=\s|$)' %
                   (SCALAR.pattern, TUPLE.pattern))
INTEGER = re.compile(r'^[0-9]+$')
CONSTANTS = {'None': None, 'True': True, 'False': False}
def read_logs(src, filt=None):
    for line in src:
        m = LOGLINE_START.match(line)
        if not m: continue
        ts, tag = m.group(1), m.group(2)
        args, idx = m.group(3), 0
        if filt and not filt(tag): continue
        values, l = {}, len(args)
        while idx < len(args):
            m = WHITESPACE.match(args, idx)
            if m:
                idx = m.end()
                continue
            m = PARAM.match(args, idx)
            if not m: break
            idx = m.end()
            name, val = m.group(1), m.group(2)
            if val in CONSTANTS:
                val = CONSTANTS[val]
            elif INTEGER.match(val):
                val = int(val)
            elif val[0] in '\'"':
                val = ast.literal_eval(val)
            elif val[0] == '(':
                if EMPTY_TUPLE.match(val):
                    val = ()
                elif TRAILING_COMMA.search(val):
                    val = ast.literal_eval(val)
                else:
                    val = ast.literal_eval('(' + val[1:-1] + ',)')
            values[name] = val
        else:
            yield (ts, tag, values)

class ArgParser:
    def __init__(self, args):
        self.args = args
        self.iter = None
        self.at_arguments = False
        self.last_option = None
    def __iter__(self):
        if self.iter is None: self.iter = iter(self.args)
        return self
    def __next__(self):
        return next(self.iter)
    def next(self):
        return next(self.iter)
    def close(self):
        try:
            next(self)
            self.toomany()
        except StopIteration:
            pass
    def argument(self, type=None):
        try:
            arg = next(self)
            if type is not None: arg = type(arg)
            return arg
        except StopIteration:
            self._die_opt('Missing required argument')
        except ValueError:
            self._die_opt('Bad argument', tail=': %r' % (arg,))
    def die(self, msg=None):
        if msg is None: raise SystemExit
        raise SystemExit('ERROR: ' + msg)
    def _die_opt(self, msg, tail=None):
        if self.last_option is not None:
            msg += ' for %r' % (self.last_option,)
        if tail is not None:
            msg += tail
        self.die(msg)
    def toomany(self):
        self.die('Too many arguments')
    def toofew(self):
        self.die('Too few arguments')
    def unknown(self):
        if self.last_option is None:
            raise RuntimeError('No option to be unknown')
        self.die('Unknown option ' + repr(self.last_option))
    def pairs(self, posmin=None, posmax=None):
        positional = 0
        for arg in self:
            if self.at_arguments or not arg.startswith('-'):
                positional += 1
                if posmax is not None and positional > posmax:
                    self.toomany()
                yield 'arg', arg
            elif arg == '--':
                self.at_arguments = True
            else:
                self.last_option = arg
                yield 'opt', arg
        if posmin is not None and positional < posmin:
            self.toofew()

class OptionParser:
    def __init__(self, progname=None):
        self.progname = progname
        self.options = collections.OrderedDict()
        self.arguments = []
        self.values = {}
        self.arg_index = 0
    def _set_accum(self, opt, kwds, default=True):
        def accum_list(list, item):
            list.append(item)
            return list
        def accum_add(accum, item):
            accum += item
            return accum
        if default and 'default' in kwds:
            opt['default'] = kwds['default']
        if 'accum' not in kwds:
            pass
        elif callable(kwds['accum']):
            opt['accum'] = kwds['accum']
        elif kwds['accum']:
            if 'default' not in opt:
                opt['default'] = []
                opt['accum'] = accum_list
            elif isinstance(opt['default'], list):
                opt['accum'] = accum_list
            else:
                opt['accum'] = accum_add
    def _make_desc(self, opt, name, placeholder):
        if name is None:
            if placeholder is None:
                res = '...'
            else:
                res = placeholder
        else:
            if placeholder is None:
                res = '--' + name
            else:
                res = '--%s %s' % (name, placeholder)
        opt['rawdesc'] = res
        if 'default' in opt or opt.get('omissible'):
            res = '[%s]' % res
        opt['desc'] = res
    def option(self, name, default=None, type=None, **kwds):
        if type is None: type = str
        try:
            placeholder = kwds['placeholder']
        except KeyError:
            placeholder = '<%s>' % type.__name__
        opt = {'option': name, 'argument': True, 'convert': type,
            'varname': kwds.get('varname', name), 'default': default,
            'help': kwds.get('help')}
        self._set_accum(opt, kwds)
        self._make_desc(opt, name, placeholder)
        self.options[name] = opt
    def flag_ex(self, name, value=True, varname=None, **kwds):
        opt = {'option': name, 'varname': varname or name, 'value': value,
            'omissible': True, 'help': kwds.get('help')}
        self._set_accum(opt, kwds)
        self._make_desc(opt, name, None)
        self.options[name] = opt
    def flag(self, name, **kwds):
        self.flag_ex(name, default=False, **kwds)
    def action(self, name, function, **kwds):
        self.options[name] = {'option': name, 'action': function,
            'rawdesc': '--%s' % name, 'desc': '[--%s]' % name,
            'help': kwds.get('help')}
    def argument(self, name=None, type=None, **kwds):
        if type is None: type = str
        try:
            placeholder = kwds['placeholder']
        except KeyError:
            placeholder = '<%s>' % name
        arg = {'varname': name, 'convert': type, 'help': kwds.get('help')}
        self._set_accum(arg, kwds)
        self._make_desc(arg, None, placeholder)
        self.arguments.append(arg)
    def help_action(self, name='help', help='Display help'):
        self.action(name, lambda: self.help(0), help=help)
    def usage(self, exit=None, write=True):
        usage = ' '.join(['USAGE:', self.progname or '...'] +
            [opt['desc'] for opt in self.options.values()] +
            [arg['desc'] for arg in self.arguments])
        if write:
            sys.stderr.write(usage + '\n')
            sys.stderr.flush()
        if exit is not None:
            sys.exit(exit)
        return usage
    def help(self, exit=None, write=True):
        help = [self.usage(write=False)]
        descs, helps = [], []
        for item in list(self.options.values()) + self.arguments:
            if not item['help']: continue
            descs.append(item['rawdesc'])
            helps.append(item['help'])
        mdl = max(map(len, descs))
        newline = '\n' + ' ' * (mdl + 2)
        for d, h in zip(descs, helps):
            help.append('%-*s: %s' % (mdl, d, h.replace('\n', newline)))
        help = '\n'.join(help)
        if write:
            sys.stderr.write(help + '\n')
            sys.stderr.flush()
        if exit is not None:
            sys.exit(exit)
        return help
    def parse(self, args):
        def process(opt, value=None):
            if opt.get('action'):
                opt['action']()
                return
            if value is None:
                try:
                    value = opt['value']
                except KeyError:
                    value = parser.argument(opt.get('convert'))
            else:
                value = opt.get('convert', str)(value)
            sv, varname = self.values, opt['varname']
            if 'accum' in opt:
                sv[varname] = opt['accum'](sv[varname], value)
            else:
                sv[varname] = value
        for item in list(self.options.values()) + self.arguments:
            if 'default' not in item: continue
            self.values.setdefault(item['varname'], item['default'])
        parser = ArgParser(args)
        for tp, arg in parser.pairs():
            if tp == 'arg':
                try:
                    process(self.arguments[self.arg_index], arg)
                    self.arg_index += 1
                except IndexError:
                    parser.toomany()
            elif arg.startswith('--'):
                try:
                    opt = self.options[arg[2:]]
                except KeyError:
                    parser.unknown()
                process(opt)
            else:
                # Single-letter options are not supported.
                parser.unknown()
    def get(self, *names, **kwds):
        force_tuple = kwds.get('force_tuple')
        try:
            if len(names) == 1 and not force_tuple:
                n = names[0]
                return self.values[n]
            ret = []
            for n in names:
                ret.append(self.values[n])
            return ret
        except KeyError:
            if n in self.options: n = '--' + n
            raise SystemExit('ERROR: Missing value for %r' % n)

class CmdlineBotBuilder:
    RELAXED_COOKIES = RELAXED_COOKIES
    def __init__(self, botcls=None, defnick=None):
        if botcls is None: botcls = HookBot
        self.botcls = botcls
        self.defnick = defnick
        self.cookies = None
        self.relaxed_cookies = self.RELAXED_COOKIES
        self.args = []
        self.kwds = {}
        self.parser = None
    def make_parser(self, *args, **kwds):
        self.parser = OptionParser(*args, **kwds)
        self.parser.help_action()
        self.parser.option('nick', self.defnick,
                           help='The nickname to use')
        self.parser.flag_ex('no-nick', None, 'nick',
                            help='Use no nickname at all')
        self.parser.option('cookies',
                           help='Cookie file (empty string -> memory)')
        self.parser.flag_ex('no-cookies', None, 'cookies',
                            help='Do not save cookies')
        self.parser.argument('url', help='The URL to connect to')
        return self.parser
    def parse(self, argv):
        self.parser.parse(argv)
        c = self.parser.get('cookies')
        if c is None:
            self.cookies = None
            self.kwds.pop('cookies', None)
        elif not c:
            self.cookies = websocket_server.cookies.CookieJar()
            self.cookies.relaxed = self.relaxed_cookies
            self.kwds['cookies'] = self.cookies
        else:
            self.cookies = websocket_server.cookies.LWPCookieJar(c,
                stat.S_IRUSR | stat.S_IWUSR)
            self.cookies.relaxed = self.relaxed_cookies
            self.cookies.load()
            self.kwds['cookies'] = self.cookies
    def add(self, *args, **kwds):
        self.args.extend(args)
        self.kwds.update(kwds)
    def add_args(self, *names):
        for n in names:
            self.kwds[n] = self.parser.get(n)
    def get_args(self, *names, **kwds):
        return self.parser.get(*names, **kwds)
    def __call__(self, *args, **kwds):
        a = [self.parser.get('url'), self.parser.get('nick')]
        a.extend(self.args)
        a.extend(args)
        k = dict(self.kwds, **kwds)
        return self.botcls(*a, **k)
