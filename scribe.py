#!/usr/bin/env python3
# -*- coding: ascii -*-

# TODO: Parse from field.

import sys, re, time, heapq, bisect, json, errno
import ast
import websocket

NICKNAME = 'Scribe'
VERSION = 'v1.2'
MAXLEN = None

class LogEntry(dict):
    @staticmethod
    def derive_timestamp(msgid):
        return (int(msgid, 16) >> 10)
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
LOGS = []

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
CONSTANTS = {'None': None, 'True': True, 'False': False}
def read_logs(src, maxlen=None):
    ret = []
    for line in src:
        m = LOGLINE_START.match(line)
        if not m: continue
        if m.group(2) not in ('POST', 'LOGPOST'): continue
        args, idx, valid = m.group(3), 0, True
        values, l = LogEntry(), len(args)
        while idx < len(args):
            m = WHITESPACE.match(args, idx)
            if m:
                idx = m.end()
                continue
            m = PARAM.match(args, idx)
            if not m:
                valid = False
                break
            idx = m.end()
            name, val = m.group(1), m.group(2)
            if val in CONSTANTS:
                val = CONSTANTS[val]
            elif val and val[0] in '\'"':
                val = ast.literal_eval(val)
            values[name] = val
        if not valid: continue
        if 'timestamp' not in values and 'id' in values:
            values['timestamp'] = LogEntry.derive_timestamp(values['id'])
        if 'text' not in values and 'content' in values:
            values['text'] = values['content']
        ret.append(values)
        if maxlen is not None and len(ret) >= maxlen * 2:
            ret.sort()
            ret = ret[-maxlen:]
    ret.sort()
    if maxlen is not None: ret = ret[-maxlen:]
    return ret

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

def log(msg):
    sys.stdout.write('[%s] %s\n' % (
        time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime()), msg))
    sys.stdout.flush()

def send(ws, msg):
    log('SEND content=%r' % msg)
    ws.send(msg)
def send_unicast(ws, dest, msg):
    seq = SEQUENCE()
    send(ws, json.dumps({'type': 'unicast', 'seq': seq, 'to': dest,
                         'data': msg}, separators=(',', ':')))
    return seq
def send_broadcast(ws, msg):
    seq = SEQUENCE()
    send(ws, json.dumps({'type': 'broadcast', 'seq': seq, 'data': msg},
                        separators=(',', ':')))
    return seq

def on_open(ws):
    def send_greetings():
        send_broadcast(ws, {'type': 'who'})
        send_broadcast(ws, {'type': 'log-query'})
        send_broadcast(ws, {'type': 'nick', 'nick': NICKNAME})
    log('OPENED')
    EVENTS.add(1, send_greetings)
def on_message(ws, msg, _context={'oid': None, 'id': None,
                                  'src': None, 'from': None}):
    def send_request(ws, rid):
        if _context['id'] != rid: return
        send_unicast(ws, _context['src'], {'type': 'log-request',
                                           'from': _context['from']})
    log('MESSAGE content=%r' % msg)
    # Try to extract message parameters.
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
            merge_logs(LOGS, [post], MAXLEN)
        elif msgt == 'log-query':
            # Someone querying how far my logs go
            if LOGS:
                send_unicast(ws, data.get('from'),
                    {'type': 'log-info', 'from': LOGS[0]['id'],
                     'to': LOGS[-1]['id'], 'length': len(LOGS)})
        elif msgt == 'log-info':
            # Log availability report
            if data.get('from') == _context['oid']:
                return
            oldest = data.get('data', {}).get('from')
            if _context['from'] is None or oldest < _context['from']:
                rid = IDENTIFIER()
                _context['id'] = rid
                _context['src'] = data.get('from')
                _context['from'] = oldest
                EVENTS.add(1, lambda: send_request(ws, rid))
        elif msgt == 'log-request':
            # Someone requesting chat logs
            lfrom = msgd.get('from')
            lto = msgd.get('to')
            amount = msgd.get('amount')
            if lfrom is None:
                fromidx = None
            else:
                fromidx = bisect.bisect_left(LOGS, lfrom)
            if lto is None:
                toidx = None
            else:
                toidx = bisect.bisect_right(LOGS, lto)
            if fromidx is not None and toidx is not None:
                ret = LOGS[fromidx:toidx]
            elif fromidx is not None:
                if amount is None:
                    ret = LOGS[fromidx:]
                else:
                    ret = LOGS[fromidx:min(len(LOGS), fromidx + amount)]
            elif toidx is not None:
                if amount is None:
                    ret = LOGS[:toidx]
                else:
                    ret = LOGS[max(0, toidx - amount):toidx]
            elif amount is not None:
                ret = LOGS[len(LOGS) - amount:]
            else:
                ret = LOGS
            reply = {'type': 'log', 'data': ret}
            if lfrom is not None: reply['from'] = lfrom
            if lto is not None: reply['to'] = lto
            if amount is not None: reply['amount'] = amount
            send_unicast(ws, data.get('from'), reply)
        elif msgt == 'log':
            raw, logs = data.get('data', {}).get('data', []), []
            for e in raw:
                if not isinstance(e, dict): continue
                logs.append(LogEntry(id=e.get('id'), parent=e.get('parent'),
                    nick=e.get('nick'), timestamp=e.get('timestamp'),
                    text=e.get('text'), **{'from': e.get('from')}))
            logs.sort()
            added = merge_logs(LOGS, logs, MAXLEN)
            for e in logs:
                eid = e['id']
                if eid not in added: continue
                log('LOGPOST id=%r parent=%r from=%r nick=%r text=%r' %
                    (eid, e['parent'], e['from'], e['nick'], e['text']))
    except (ValueError, TypeError, AttributeError) as e:
        return
def on_error(ws, exc):
    log('ERROR reason=%r' % repr(exc))
def on_close(ws):
    log('CLOSED')

def main():
    global MAXLEN
    def settimeout(ws, t):
        if t is None or t > 0:
            ws.settimeout(t)
        else:
            ws.settimeout(0)
    try:
        it, addr, toread, at_args = iter(sys.argv[1:]), None, [], False
        for arg in it:
            if arg.startswith('-') and not at_args:
                if arg == '--help':
                    sys.stderr.write('USAGE: %s [--help] [--maxlen maxlen] '
                        '[--read-file file] url\n' % sys.argv[0])
                    sys.exit(0)
                elif arg == '--maxlen':
                    MAXLEN = int(next(it))
                elif arg == '--read-file':
                    toread.append(next(it))
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
    log('SCRIBE version=%s' % VERSION)
    for fn in toread:
        log('READING file=%r maxlen=%r' % (fn, MAXLEN))
        try:
            with open(fn) as f:
                merge_logs(LOGS, read_logs(f, MAXLEN), MAXLEN)
        except IOError as e:
            log('ERROR reason=%r' % repr(e))
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
    except KeyboardInterrupt:
        log('INTERRUPTED')
        if ws: on_close(ws)

if __name__ == '__main__': main()
