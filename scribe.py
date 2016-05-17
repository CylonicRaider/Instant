#!/usr/bin/env python3
# -*- coding: ascii -*-

import sys, re, time
import heapq, bisect
import contextlib
import signal, errno, ssl
import ast, json
import websocket
import sqlite3

NICKNAME = 'Scribe'
VERSION = 'v1.3'
MAXLEN = None
DONTSTAY = False
DONTPULL = False

def parse_version(s):
    if s.startswith('v'): s = s[1:]
    try:
        return tuple(map(int, s.split('.')))
    except (TypeError, ValueError):
        return ()

class LogEntry(dict):
    @staticmethod
    def derive_timestamp(msgid):
        # NOTE: Returns milliseconds since Epoch.
        if isinstance(msgid, int):
            return msgid >> 10
        else:
            return int(msgid, 16) >> 10
    def __cmp__(self, other):
        if isinstance(other, LogEntry):
            oid = other['id']
        elif isinstance(other, str):
            oid = other
        else:
            raise NotImplementedError()
        sid = self['id']
        return 0 if sid == oid else 1 if sid > oid else -1
    def __gt__(self, other):
        return self.__cmp__(other) > 0
    def __ge__(self, other):
        return self.__cmp__(other) >= 0
    def __eq__(self, other):
        return self.__cmp__(other) == 0
    def __ne__(self, other):
        return self.__cmp__(other) != 0
    def __le__(self, other):
        return self.__cmp__(other) <= 0
    def __lt__(self, other):
        return self.__cmp__(other) < 0

class LogDB:
    def __init__(self):
        self.maxlen = MAXLEN
    def capacity(self):
        return self.maxlen
    def bounds(self):
        raise NotImplementedError
    def get(self, index):
        raise NotImplementedError
    def query(self, lfrom=None, lto=None, amount=None):
        raise NotImplementedError
    def append(self, entry):
        return bool(self.extend((entry,)))
    def extend(self, entries):
        raise NotImplementedError
    def close(self):
        pass

class LogDBList(LogDB):
    @staticmethod
    def merge_logs(base, add, maxlen=None):
        seen, added = set(i['id'] for i in base), set()
        for e in add:
            eid = e['id']
            if eid in seen: continue
            seen.add(eid)
            added.add(eid)
            base.append(e)
        base.sort()
        if maxlen:
            base[:] = base[-maxlen:]
        return added
    def __init__(self):
        LogDB.__init__(self)
        self.data = []
    def bounds(self):
        if not self.data:
            return (None, None, None)
        else:
            return (self.data[0]['id'], self.data[-1]['id'], len(self.data))
    def get(self, index):
        return self.data[index]
    def query(self, lfrom=None, lto=None, amount=None):
        if lfrom is None:
            fromidx = None
        else:
            fromidx = bisect.bisect_left(self.data, lfrom)
        if lto is None:
            toidx = None
        else:
            toidx = bisect.bisect_right(self.data, lto)
        if fromidx is not None and toidx is not None:
            ret = self.data[fromidx:toidx]
        elif fromidx is not None:
            if amount is None:
                ret = self.data[fromidx:]
            else:
                ret = self.data[fromidx:min(len(self.data),
                                            fromidx + amount)]
        elif toidx is not None:
            if amount is None:
                ret = self.data[:toidx]
            else:
                ret = self.data[max(0, toidx - amount):toidx]
        elif amount is not None:
            ret = self.data[len(self.data) - amount:]
        else:
            ret = self.data
        return ret
    def extend(self, entries):
        return self.merge_logs(self.data, entries, self.maxlen)

class LogDBSQLite(LogDB):
    @staticmethod
    def make_msgid(key):
        return (None if key is None else '%016X' % key)
    @staticmethod
    def make_key(msgid):
        return (None if msgid is None else int(msgid, 16))
    @staticmethod
    def make_strkey(msgid):
        return (None if msgid is None else str(int(msgid, 16)))
    def __init__(self, filename):
        LogDB.__init__(self)
        self.filename = filename
        self.conn = sqlite3.connect(filename)
        self.cursor = self.conn.cursor()
        self.init()
    def init(self):
        self.cursor.execute('CREATE TABLE IF NOT EXISTS logs ('
                                'msgid INTEGER PRIMARY KEY,'
                                'parent INTEGER,' # REFERENCES msgid
                                'sender INTEGER,'
                                'nick TEXT,'
                                'text TEXT'
                            ')')
        self.conn.commit()
    def capacity(self):
        return None
    def _wrap(self, row):
        msgid, parent, sender, nick, text = row
        return LogEntry(id=self.make_msgid(msgid),
                        parent=self.make_msgid(parent),
                        nick=nick,
                        text=text,
                        timestamp=LogEntry.derive_timestamp(msgid),
                        **{'from': self.make_msgid(sender)})
    def _unwrap(self, entry):
        return (self.make_key(entry['id']),
                self.make_key(entry['parent']),
                self.make_key(entry['from']),
                entry['nick'],
                entry['text'])
    def _try_unwrap(self, entry):
        # People are known to have actually injected bad message ID-s
        try:
            return self._unwrap(entry)
        except (KeyError, ValueError):
            return None
    def bounds(self):
        self.cursor.execute('SELECT MIN(msgid), MAX(msgid), '
                                'COUNT(msgid) FROM logs')
        first, last, count = self.cursor.fetchone()
        if not count: count = None
        return (self.make_msgid(first), self.make_msgid(last), count)
    def get(self, index):
        if index >= 0:
            self.cursor.execute('SELECT * FROM logs '
                                'ORDER BY msgid ASC '
                                'LIMIT 1 OFFSET ?', (str(index),))
        else:
            self.cursor.execute('SELECT * FROM logs '
                                'ORDER BY msgid DESC '
                                'LIMIT 1 OFFSET ?', (str(-index - 1),))
        res = self.cursor.fetchone()
        if res is None: return None
        return self._unwrap(res)
    def query(self, lfrom=None, lto=None, amount=None):
        fromkey = self.make_strkey(lfrom)
        tokey = self.make_strkey(lto)
        if amount is None:
            amount = None if self.maxlen is None else str(self.maxlen)
        else:
            amount = str(amount)
        flip = False
        if fromkey is not None and tokey is not None:
            stmt = ('SELECT * FROM logs '
                    'WHERE msgid BETWEEN ? AND ? '
                    'ORDER BY msgid ASC', (fromkey, tokey))
        elif fromkey is not None:
            if amount is not None:
                stmt = ('SELECT * FROM logs '
                        'WHERE msgid >= ? '
                        'ORDER BY msgid ASC '
                        'LIMIT ?', (fromkey, amount))
            else:
                stmt = ('SELECT * FROM logs '
                        'WHERE msgid >= ? '
                        'ORDER BY msgid ASC', (fromkey,))
        elif tokey is not None:
            if amount is not None:
                stmt = ('SELECT * FROM logs '
                        'WHERE msgid <= ? '
                        'ORDER BY msgid DESC '
                        'LIMIT ?', (tokey, amount))
            else:
                stmt = ('SELECT * FROM logs '
                        'WHERE msgid <= ? '
                        'ORDER BY msgid DESC', (tokey,))
            flip = True
        elif amount is not None:
            stmt = ('SELECT * FROM logs '
                    'ORDER BY msgid DESC '
                    'LIMIT ?', (amount,))
            flip = True
        else:
            stmt = ('SELECT * FROM logs '
                    'ORDER BY msgid',)
        self.cursor.execute(*stmt)
        data = self.cursor.fetchall()
        if flip: data.reverse()
        return list(map(self._wrap, data))
    def append(self, entry):
        row = self._try_unwrap(entry)
        if not row: return False
        self.cursor.execute('INSERT OR REPLACE INTO logs ('
            'msgid, parent, sender, nick, text) VALUES (?, ?, ?, ?, ?)',
            row)
        self.conn.commit()
        return True
    def extend(self, entries):
        added = []
        for e in entries:
            sk = self.make_strkey(e['id'])
            self.cursor.execute('SELECT 1 FROM logs WHERE msgid = ?',
                                (sk,))
            if not self.cursor.fetchone(): added.append(e['id'])
        added.sort()
        rows = filter(None, map(self._try_unwrap, entries))
        self.cursor.executemany('INSERT OR REPLACE INTO logs ('
            'msgid, parent, sender, nick, text) VALUES (?, ?, ?, ?, ?)',
            rows)
        self.conn.commit()
        return added
    def close(self):
        self.conn.close()

LOGS = LogDBList()

class Sequence:
    def __init__(self):
        self.value = 0
    def __call__(self):
        ret = self.value
        self.value += 1
        return ret
SEQUENCE = Sequence()
IDENTIFIER = Sequence()

class EventScheduler:
    @staticmethod
    def sleep(delay):
        if delay is None:
            time.sleep(None)
        elif delay > 0:
            time.sleep(delay)
    class Event:
        def __init__(self, time, callback):
            self.time = time
            self.callback = callback
        def __call__(self):
            self.callback()
        def __gt__(self, other):
            return self.time > other.time
        def __ge__(self, other):
            return self.time >= other.time
        def __eq__(self, other):
            return self.time == other.time
        def __ne__(self, other):
            return self.time != other.time
        def __le__(self, other):
            return self.time <= other.time
        def __lt__(self, other):
            return self.time < other.time
    def __init__(self, time, sleep):
        self.pending = []
        self.time = time
        self.sleep = sleep
    def add_abs(self, timestamp, callback):
        heapq.heappush(self.pending, self.Event(timestamp, callback))
    def add(self, delay, callback):
        return self.add_abs(self.time() + delay, callback)
    def clear(self):
        self.pending[:] = []
    def run(self):
        try:
            while self.pending:
                now = self.time()
                head = self.pending[0]
                if head.time > now: return
                heapq.heappop(self.pending)
                head()
        finally:
            if self.pending:
                diff = self.time() - self.pending[0].time
                self.sleep(diff)
            else:
                self.sleep(None)
    def main(self):
        while self.pending:
            self.run()
EVENTS = EventScheduler(time.time, time.sleep)

LOGLINE_START = re.compile(r'^\[([0-9 Z:-]+)\]\s+([A-Z_-]+)\s+(.*)$')
WHITESPACE = re.compile(r'\s+')
PARAM = re.compile(r'([a-zA-Z0-9_-]+)=([^"\']\S*'
    r'|"([^"\\]|\\.)*"|\'([^\'\\]|\\.)*\')(?=\s|$)')
INTEGER = re.compile(r'^[0-9]+$')
CONSTANTS = {'None': None, 'True': True, 'False': False}
def read_logs(src):
    for line in src:
        m = LOGLINE_START.match(line)
        if not m: continue
        ts, tag = m.group(1), m.group(2)
        args, idx = m.group(3), 0
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
            elif val and val[0] in '\'"':
                val = ast.literal_eval(val)
            values[name] = val
        else:
            yield (ts, tag, values)
def read_posts(src, maxlen=None):
    cver, froms, ret = (), {}, []
    for ts, tag, values in read_logs(src):
        if tag == 'SCRIBE':
            cver = parse_version(values.get('version'))
            continue
        if tag in ('POST', 'LOGPOST'):
            pass
        elif cver < (1, 2) and tag == 'MESSAGE':
            try:
                msg = json.loads(values.get('content'))
            except (TypeError, ValueError):
                continue
            if msg.get('type') not in ('broadcast', 'unicast'):
                continue
            try:
                msgid = msg['id']
            except KeyError:
                continue
            msgd = msg.get('data', {})
            if msgd.get('type') == 'post':
                froms[msgid] = msg.get('from')
            elif msgd.get('type') == 'log':
                for v in msgd.get('data', ()):
                    try:
                        froms[v['id']] = v['from']
                    except KeyError:
                        pass
            continue
        else:
            continue
        if 'id' not in values: continue
        values = LogEntry(values)
        if 'timestamp' not in values:
            values['timestamp'] = LogEntry.derive_timestamp(values['id'])
        if 'text' not in values and 'content' in values:
            values['text'] = values['content']
            del values['content']
        ret.append(values)
        if maxlen is not None and len(ret) >= maxlen * 2:
            ret.sort()
            ret = ret[-maxlen:]
    ret.sort()
    if maxlen is not None: ret = ret[-maxlen:]
    for e in ret:
        if 'from' not in ret and e['id'] in froms:
            e['from'] = froms[e['id']]
    return ret

def log(msg):
    m = '[%s] %s\n' % (time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime()),
                       msg)
    sys.stdout.write(m.encode('ascii', 'backslashreplace').decode('ascii'))
    sys.stdout.flush()

def send(ws, msg, verbose=True):
    if verbose: log('SEND content=%r' % msg)
    ws.send(msg)
def send_unicast(ws, dest, msg, verbose=True):
    seq = SEQUENCE()
    send(ws, json.dumps({'type': 'unicast', 'seq': seq, 'to': dest,
                         'data': msg}, separators=(',', ':')), verbose)
    return seq
def send_broadcast(ws, msg, verbose=True):
    seq = SEQUENCE()
    send(ws, json.dumps({'type': 'broadcast', 'seq': seq, 'data': msg},
                        separators=(',', ':')), verbose)
    return seq

def send_logs(ws, peer, lfrom=None, lto=None, amount=None):
    ret = LOGS.query(lfrom, lto, amount)
    reply = {'type': 'log', 'data': ret}
    if lfrom is not None: reply['from'] = lfrom
    if lto is not None: reply['to'] = lto
    if amount is not None: reply['amount'] = amount
    if ret:
        log('LOGSEND to=%r log-from=%r log-to=%r log-count=%r' % (
            peer, ret[0]['id'], ret[-1]['id'], len(ret)))
    else:
        log('LOGSEND to=%r log-count=0' % peer)
    return send_unicast(ws, peer, reply, False)

def on_open(ws):
    def send_greetings():
        send_broadcast(ws, {'type': 'who'})
        if not DONTPULL:
            send_broadcast(ws, {'type': 'log-query'})
        send_broadcast(ws, {'type': 'nick', 'nick': NICKNAME})
    log('OPENED')
    EVENTS.add(1, send_greetings)
def on_message(ws, msg, _context={'oid': None, 'id': None, 'src': None,
                                  'from': None, 'to': None}):
    def send_request(ws, rid):
        if _context['id'] != rid: return
        send_unicast(ws, _context['src'], {'type': 'log-request',
                                           'to': _context['to']})
    log('MESSAGE content=%r' % msg)
    # Try to extract message parameters
    try:
        data = json.loads(msg)
        if data.get('type') == 'identity':
            _context['oid'] = data.get('data', {}).get('id')
        elif data.get('type') not in ('unicast', 'broadcast'):
            return
        msgd = data.get('data', {})
        msgt = msgd.get('type')
        # Protocollary replies / other handling
        if msgt == 'who':
            # Own nick
            send_unicast(ws, data.get('from'), {'type': 'nick',
                                                'nick': NICKNAME})
        elif msgt == 'nick':
            log('NICK id=%r nick=%r' % (data.get('from'), msgd.get('nick')))
        elif msgt == 'post':
            # A single mesage
            post = LogEntry(id=data.get('id'), parent=msgd.get('parent'),
                            nick=msgd.get('nick'), text=msgd.get('text'),
                            timestamp=data.get('timestamp'),
                            **{'from': data.get('from')})
            log('POST id=%r parent=%r from=%r nick=%r text=%r' %
                (post['id'], post['parent'], post['from'], post['nick'],
                 post['text']))
            LOGS.append(post)
        elif msgt == 'log-query':
            # Someone querying how far my logs go
            bounds = LOGS.bounds()
            if bounds[2]:
                send_unicast(ws, data.get('from'),
                    {'type': 'log-info', 'from': bounds[0],
                     'to': bounds[1], 'length': bounds[2]})
        elif msgt == 'log-info':
            # Log availability report
            if data.get('from') == _context['oid'] or DONTPULL:
                return
            oldest = data.get('data', {}).get('from')
            if oldest is None:
                pass
            elif _context['from'] is None or oldest < _context['from']:
                rid = IDENTIFIER()
                _context['id'] = rid
                _context['src'] = data.get('from')
                _context['from'] = oldest
                _context['to'] = LOGS.bounds()[0]
                EVENTS.add(1, lambda: send_request(ws, rid))
        elif msgt == 'log-request':
            # Someone requesting chat logs
            send_logs(ws, data.get('from'), msgd.get('from'),
                      msgd.get('to'), msgd.get('amount'))
        elif msgt == 'log':
            # Someone delivering chat logs
            if DONTPULL: return
            raw, logs = data.get('data', {}).get('data', []), []
            for e in raw:
                if not isinstance(e, dict): continue
                logs.append(LogEntry(id=e.get('id'), parent=e.get('parent'),
                    nick=e.get('nick'), timestamp=e.get('timestamp'),
                    text=e.get('text'), **{'from': e.get('from')}))
            logs.sort()
            added = LOGS.extend(logs)
            for e in logs:
                eid = e['id']
                if eid not in added: continue
                log('LOGPOST id=%r parent=%r from=%r nick=%r text=%r' %
                    (eid, e['parent'], e['from'], e['nick'], e['text']))
            # Request more if applicable
            oldest = LOGS.bounds()[0]
            oldestRemote = data.get('data', {}).get('from')
            if oldestRemote is None: oldestRemote = _context['from']
            if (oldestRemote is not None and (oldest is None or
                                              oldestRemote < oldest)):
                _context['id'] = None
                _context['src'] = None
                _context['from'] = None
                _context['to'] = None
                send_broadcast(ws, {'type': 'log-query'})
            else:
                send_broadcast(ws, {'type': 'log-done'})
                if DONTSTAY:
                    raise SystemExit
        elif msgt == 'log-done':
            # Logs were transferred
            if DONTPULL and DONTSTAY:
                raise SystemExit
    except (ValueError, TypeError, AttributeError) as e:
        log('FAULT reason=%r' % repr(e))
        return
def on_error(ws, exc):
    log('ERROR reason=%r' % repr(exc))
def on_close(ws):
    log('CLOSED')

def main():
    global LOGS, NICKNAME, DONTSTAY, DONTPULL
    @contextlib.contextmanager
    def openarg(fname):
        if fname == '-':
            yield sys.stdin
        else:
            with open(fname) as f:
                yield f
    def interrupt(signum, frame):
        raise SystemExit
    def settimeout(ws, t):
        if t is None or t > 0:
            ws.settimeout(t)
        else:
            ws.settimeout(0)
    def run_push_logs():
        while push_logs:
            peer = push_logs.pop(0)
            log('LOGPUSH to=%r' % peer)
            send_logs(ws, peer)
    try:
        it, at_args = iter(sys.argv[1:]), False
        maxlen, toread, msgdb, push_logs = MAXLEN, [], None, []
        addr = None
        for arg in it:
            if arg.startswith('-') and not at_args:
                if arg == '--help':
                    sys.stderr.write('USAGE: %s [--help] [--maxlen maxlen] '
                        '[--msgdb file] [--read-file file] [--push-logs id] '
                        '[--dont-stay] [--dont-pull] [--nick name] url\n' %
                        sys.argv[0])
                    sys.exit(0)
                elif arg == '--maxlen':
                    maxlen = int(next(it))
                elif arg == '--read-file':
                    toread.append(next(it))
                elif arg == '--msgdb':
                    msgdb = next(it)
                elif arg == '--push-logs':
                    push_logs.append(next(it))
                elif arg == '--dont-stay':
                    DONTSTAY = True
                elif arg == '--dont-pull':
                    DONTPULL = True
                elif arg == '--nick':
                    NICKNAME = next(it)
                elif arg == '--':
                    at_args = True
                else:
                    sys.stderr.write('ERROR: Unknown option: %r\n' % arg)
                    sys.exit(1)
                continue
            if addr is not None:
                sys.stderr.write('ERROR: More than one address specified.\n')
                sys.exit(1)
            addr = arg
    except StopIteration:
        sys.stderr.write('ERROR: Missing required argument for %s!\n' % arg)
        sys.exit(1)
    except ValueError:
        sys.stderr.write('ERROR: Invalid valid for %s!\n' % arg)
        sys.exit(1)
    if addr is None:
        sys.stderr.write('ERROR: No address specified.\n')
        sys.exit(1)
    try:
        signal.signal(signal.SIGINT, interrupt)
    except Exception:
        pass
    try:
        signal.signal(signal.SIGTERM, interrupt)
    except Exception:
        pass
    log('SCRIBE version=%s' % VERSION)
    if msgdb is not None:
        LOGS = LogDBSQLite(msgdb)
    if maxlen is not None:
        LOGS.maxlen = maxlen
    for fn in toread:
        log('READING file=%r maxlen=%r' % (fn, LOGS.capacity()))
        try:
            with openarg(fn) as f:
                LOGS.extend(read_posts(f, LOGS.capacity()))
        except IOError as e:
            log('ERROR reason=%r' % repr(e))
    log('LOGBOUNDS from=%r to=%r amount=%r' % LOGS.bounds())
    ws = None
    try:
        while 1:
            log('CONNECT url=%r' % addr)
            try:
                ws = websocket.create_connection(addr)
            except Exception as e:
                log('ERROR reason=%r' % repr(e))
                time.sleep(10)
                continue
            EVENTS.sleep = lambda t: settimeout(ws, t)
            EVENTS.clear()
            EVENTS.add(0.5, run_push_logs)
            on_open(ws)
            while 1:
                EVENTS.run()
                try:
                    msg = ws.recv()
                except websocket.WebSocketTimeoutException as e:
                    continue
                except websocket.WebSocketConnectionClosedException as e:
                    break
                except IOError as e:
                    if (e.errno == errno.EAGAIN or
                            e.errno == errno.EWOULDBLOCK):
                        continue
                    elif isinstance(e, ssl.SSLError) and e.args[0] == 2:
                        # SSLWantReadError -- retry.
                        continue
                    on_error(ws, e)
                    break
                except Exception as e:
                    on_error(ws, e)
                    break
                on_message(ws, msg)
            EVENTS.sleep = time.sleep
            on_close(ws)
            ws = None
            time.sleep(10)
    except (KeyboardInterrupt, SystemExit) as e:
        try:
            if isinstance(e, SystemExit):
                log('EXITING')
            else:
                log('INTERRUPTED')
            if ws: on_close(ws)
        except Exception:
            pass
    finally:
        LOGS.close()

if __name__ == '__main__': main()
