# -*- coding: ascii -*-

import sys, re, time
import traceback
import heapq
import json
import ast
import threading

import websocket

try:
    from queue import Queue, Empty as QueueEmpty
except ImportError:
    from Queue import Queue, Empty as QueueEmpty

VERSION = 'v1.4'

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
    def __init__(self, url, **kwds):
        self.url = url
        self.timeout = kwds.get('timeout', self.TIMEOUT)
        self.ws = None
        self.sequence = AtomicSequence()
        self._wslock = threading.RLock()
    def connect(self):
        with self._wslock:
            if self.ws is not None: return
            self.ws = websocket.create_connection(self.url)
            if self.timeout is not None:
                self.ws.settimeout(self.timeout)
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
        return ws.recv()
    def send_raw(self, rawmsg):
        ws = self.ws
        if ws is None: raise websocket.WebSocketConnectionClosedException
        ws.send(rawmsg)
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
                except websocket.WebSocketTimeoutException as exc:
                    self.on_timeout(exc)
                    continue
                if rawmsg is None: break
                self.on_message(rawmsg)
        except websocket.WebSocketConnectionClosedException:
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
        if data.get('type') == 'who' and peer != self.identity['id']:
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
SCALAR = re.compile(r'[^"\'\x28,\s]\S*|"(?:[^"\\]|\\.)*"|'
    r'\'(?:[^\'\\]|\\.)*\'')
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

def argparse(args, cmd=None):
    it, option = iter(args), None
    while 1:
        if isinstance(cmd, (tuple, list)):
            cmd, cmdarg = cmd
        elif callable(cmd):
            cmd, cmdarg = 'convert', cmd
        else:
            cmd, cmdarg = cmd, None
        foropt = (' for %r' % (option,) if option is not None else '')
        if cmd is None or cmd == 'next':
            value = next(it)
            option = value
        elif cmd == 'arg':
            try:
                value = next(it)
            except StopIteration:
                raise SystemExit('ERROR: Missing required argument' + foropt)
        elif cmd == 'convert':
            try:
                raw = next(it)
                value = cmdarg(raw)
            except StopIteration:
                raise SystemExit('ERROR: Missing required argument' + foropt)
            except ValueError:
                raise SystemExit('ERROR: Bad argument %s: %r' %
                                 (foropt, raw))
        elif cmd == 'close':
            next(it)
            raise SystemExit('ERROR: Too many arguments')
        elif cmd == 'unknown':
            if option is None:
                raise RuntimeError("No option for 'unknown' command")
            raise SystemExit('ERROR: Unknown option %r' % (option,))
        elif cmd == 'toofew':
            raise SystemExit('ERROR: Too few arguments')
        elif cmd == 'toomany':
            raise SystemExit('ERROR: Too many arguments')
        elif cmd == 'die':
            if cmdarg is None: raise SystemExit
            raise SystemExit('ERROR: %s' % (cmdarg,))
        else:
            raise ValueError('Bad argparse command: %r' % (cmd,))
        try:
            cmd = yield value
        except GeneratorExit:
            cmd = 'close'
