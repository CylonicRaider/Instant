#!/usr/bin/env python3
# -*- coding: ascii -*-

import sys, re, time
import threading
import heapq, bisect
import contextlib
import signal, errno, ssl
import traceback
import ast, json
import sqlite3

import websocket
import instabot

NICKNAME = 'Scribe'
VERSION = instabot.VERSION
MAXLEN = None
DONTSTAY = False
DONTPULL = False

PING_DELAY = 2700 # 45 min

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
    def init(self):
        pass
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
    def delete(self, ids):
        raise NotImplementedError
    def append_uuid(self, uid, uuid):
        raise NotImplementedError
    def extend_uuid(self, mapping):
        ret = []
        for k, v in mapping.items():
            if self.append_uuid(k, v): ret.append(k)
        return ret
    def get_uuid(self, uid):
        raise NotImplementedError
    def query_uuid(self, ids=None):
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
        self.uuids = {}
        self._uuid_list = []
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
    def delete(self, ids):
        idset, ndata, ret = set(ids), [], []
        for i in self.data:
            (ret if i['id'] in idset else ndata).append(i)
        self.data[:] = ndata
        return ret
    def append_uuid(self, uid, uuid):
        ret = (uid not in self.uuids)
        self.uuids[uid] = uuid
        self._uuid_list.append(uid)
        if self.maxlen is not None and len(self._uuid_list) > self.maxlen:
            c = len(self._uuid_list) - self.maxlen
            for u in self._uuid_list[:c]:
                del self.uuids[u]
            self._uuid_list = self._uuid_list[c:]
        return ret
    def get_uuid(self, uid):
        return self.uuids[uid]
    def query_uuid(self, ids=None):
        if not ids: return self.uuids
        ret = {}
        for u in ids:
            try:
                ret[u] = self.uuids[u]
            except KeyError:
                pass
        return ret

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
    def init(self):
        self.conn = sqlite3.connect(filename)
        self.cursor = self.conn.cursor()
        # The REFERENCES is not enforced to allow "stray" messages to
        # be preserved.
        self.cursor.execute('CREATE TABLE IF NOT EXISTS logs ('
                                'msgid INTEGER PRIMARY KEY,'
                                'parent INTEGER,' # REFERENCES msgid
                                'sender INTEGER,'
                                'nick TEXT,'
                                'text TEXT'
                            ')')
        self.cursor.execute('CREATE TABLE IF NOT EXISTS uuid ('
                                'user INTEGER PRIMARY KEY,'
                                'uuid TEXT'
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
    def delete(self, ids):
        ret, msgids = [], [self.make_key(i) for i in ids]
        for i in msgids:
            self.cursor.execute('SELECT * FROM logs WHERE msgid = ?', (i,))
            ret.extend(self.cursor.fetchall())
        self.cursor.executemany('DELETE FROM logs WHERE msgid = ?',
                                ((i,) for i in msgids))
        self.conn.commit()
        return list(map(self._wrap, ret))
    def append_uuid(self, uid, uuid):
        key = self.make_strkey(uid)
        try:
            self.cursor.execute('INSERT INTO uuid (user, uuid) '
                'VALUES (?, ?)', (key, uuid))
            self.conn.commit()
            return True
        except sqlite3.IntegrityError:
            self.cursor.execute('UPDATE uuid SET uuid = ? WHERE user = ?',
                                (uuid, key))
            self.conn.commit()
            return False
    def extend_uuid(self, mapping):
        ret = []
        for k in mapping.keys():
            self.cursor.execute('SELECT 1 FROM uuid WHERE user = ?',
                                (self.make_strkey(k),))
            if not self.cursor.fetchone(): ret.append(k)
        self.cursor.executemany('INSERT OR REPLACE INTO uuid (user, uuid) '
            'VALUES (?, ?)',
            ((self.make_strkey(k), v) for k, v in mapping.items()))
        self.conn.commit()
        return ret
    def get_uuid(self, uid):
        self.cursor.execute('SELECT uuid FROM uuid WHERE user = ?',
                            (self.make_strkey(uid),))
        res = self.cursor.fetchone()
        return (None if res is None else res[0])
    def query_uuid(self, ids=None):
        ret = {}
        if not ids:
            if self.maxlen is None:
                self.cursor.execute('SELECT user, uuid FROM uuid '
                                    'ORDER BY user DESC')
            else:
                self.cursor.execute('SELECT user, uuid FROM uuid '
                                    'ORDER BY user DESC '
                                    'LIMIT ?', (str(self.maxlen),))
            for k, v in self.cursor:
                ret[k] = v
            return ret
        for u in ids:
            uuid = self.get_uuid(u)
            if uuid is not None: ret[u] = uuid
        return ret
    def close(self):
        pass
    def close(self):
        self.conn.close()

LOGS = LogDBList()

SEQUENCE = instabot.AtomicSequence()
IDENTIFIER = instabot.AtomicSequence()

EVENTS = instabot.EventScheduler()

def read_posts_ex(src, maxlen=None):
    def truncate(ret, uuids):
        delset, kset = set(dels), set(sorted(uuids)[-maxlen:])
        ret = [i for i in ret if i['id'] not in delset]
        ret.sort()
        ret = ret[-maxlen:]
        uuids = dict((k, v) for k, v in uuids.items() if k in kset)
        dels[:] = []
        return (ret, uuids)
    TAGS = frozenset(('SCRIBE', 'POST', 'LOGPOST', 'MESSAGE', 'DELETE',
                      'UUID'))
    cver, froms, dels, ret, uuids = (), {}, [], [], {}
    for ts, tag, values in instabot.read_logs(src, TAGS.__contains__):
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
        elif tag == 'DELETE':
            try:
                dels.append(values['id'])
            except KeyError:
                pass
            continue
        elif tag == 'UUID':
            try:
                uuids[values['id']] = values['uuid']
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
        if maxlen is not None and len(ret) >= 2 * maxlen:
            ret, uuids = truncate(ret, uuids)
    if maxlen is not None:
        ret, uuids = truncate(ret, uuids)
    ret.sort(key=lambda x: x['id'])
    for e in ret:
        if 'from' not in e and e['id'] in froms:
            e['from'] = froms[e['id']]
    return (ret, uuids)
def read_posts(src, maxlen=None):
    return read_posts_ex(src, maxlen)[0]

log = instabot.log

def send(ws, msg, verbose=True):
    if verbose: log('SEND content=%r' % (msg,))
    ws.send(msg)
def send_seq(ws, msg, verbose=True):
    seq = SEQUENCE()
    msg['seq'] = seq
    send(ws, json.dumps(msg, separators=(',', ':')), verbose)
    return seq
def send_unicast(ws, dest, msg, verbose=True):
    return send_seq(ws, {'type': 'unicast', 'to': dest, 'data': msg},
                    verbose)
def send_broadcast(ws, msg, verbose=True):
    return send_seq(ws, {'type': 'broadcast', 'data': msg}, verbose)

def send_logs(ws, peer, lfrom=None, lto=None, amount=None, key=None):
    ret = LOGS.query(lfrom, lto, amount)
    reply = {'type': 'log', 'data': ret,
             'uuids': LOGS.query_uuid(ent['from'] for ent in ret)}
    if lfrom is not None: reply['from'] = lfrom
    if lto is not None: reply['to'] = lto
    if amount is not None: reply['amount'] = amount
    if key is not None: reply['key'] = key
    ls = 'LOGSEND to=%r' % (peer,)
    if ret:
        ls += ' log-from=%r log-to=%r log-count=%r' % (ret[0]['id'],
          ret[-1]['id'], len(ret))
    else:
        ls += ' log-count=0'
    if key:
        ls += ' key=%r' % (key,)
    log(ls)
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
                                  'from': None, 'to': None, 'done': False,
                                  'uuid': None}):
    def send_request(ws, rid):
        if rid is Ellipsis:
            if _context['id'] is None:
                _context['done'] = True
            return
        elif rid !=  _context['id']:
            return
        send_unicast(ws, _context['src'], {'type': 'log-request',
                                           'to': _context['to']})
    def add_uuid(uid, uuid):
        if uid and uuid and LOGS.append_uuid(uid, uuid):
            log('UUID id=%r uuid=%r' % (uid, uuid))
    log('MESSAGE content=%r' % (msg,))
    # Try to extract message parameters
    try:
        data = json.loads(msg)
        msgd = data.get('data', {})
        if data.get('type') == 'identity':
            _context['oid'] = msgd.get('id')
            _context['uuid'] = msgd.get('uuid')
            add_uuid(_context['oid'], _context['uuid'])
            return
        elif data.get('type') not in ('unicast', 'broadcast'):
            return
        msgt = msgd.get('type')
        # Protocollary replies / other handling
        if msgt == 'joined':
            add_uuid(data.get('from'), msgd.get('uuid'))
        elif msgt == 'who':
            # Own nick
            reply = {'type': 'nick', 'nick': NICKNAME}
            if _context['uuid']: reply['uuid'] = _context['uuid']
            send_unicast(ws, data.get('from'), reply)
        elif msgt == 'nick':
            uuid = msgd.get('uuid')
            if uuid:
                log('NICK id=%r uuid=%r nick=%r' % (data.get('from'),
                    uuid, msgd.get('nick')))
                add_uuid(data.get('from'), uuid)
            else:
                log('NICK id=%r nick=%r' % (data.get('from'),
                                            msgd.get('nick')))
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
            if data.get('from') == _context['oid']:
                EVENTS.add(1, lambda: send_request(ws, Ellipsis))
            elif DONTPULL:
                return
            oldest = msgd.get('from')
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
                      msgd.get('to'), msgd.get('amount'), msgd.get('key'))
        elif msgt == 'log':
            # Someone delivering chat logs
            if DONTPULL: return
            raw, logs = msgd.get('data', []), []
            for e in raw:
                if not isinstance(e, dict): continue
                logs.append(LogEntry(id=e.get('id'), parent=e.get('parent'),
                    nick=e.get('nick'), timestamp=e.get('timestamp'),
                    text=e.get('text'), **{'from': e.get('from')}))
            logs.sort()
            added = set(LOGS.extend(logs))
            for e in logs:
                eid = e['id']
                if eid not in added: continue
                log('LOGPOST id=%r parent=%r from=%r nick=%r text=%r' %
                    (eid, e['parent'], e['from'], e['nick'], e['text']))
            uuids = msgd.get('uuids', {})
            uuid_added = LOGS.extend_uuid(uuids)
            uuid_added.sort()
            for k in uuid_added:
                log('LOGUUID id=%r uuid=%r' % (k, uuids[k]))
            # Request more if applicable
            oldest = LOGS.bounds()[0]
            oldestRemote = msgd.get('from')
            if oldestRemote is None: oldestRemote = _context['from']
            if (oldestRemote is not None and (oldest is None or
                                              oldestRemote < oldest)):
                _context['id'] = None
                _context['src'] = None
                _context['from'] = None
                _context['to'] = None
                send_broadcast(ws, {'type': 'log-query'})
            else:
                _context['done'] = True
                send_broadcast(ws, {'type': 'log-done'})
                if DONTSTAY:
                    raise SystemExit
        elif msgt == 'delete':
            for msg in LOGS.delete(msgd.get('ids', [])):
                log('DELETE id=%r parent=%r from=%r nick=%r text=%r' %
                    (msg['id'], msg['parent'], msg['from'], msg['nick'],
                     msg['text']))
        elif msgt == 'log-inquiry':
            # Someone asks whether we're done with pulling logs
            if _context['done']:
                send_unicast(ws, data.get('from'), {'type': 'log-done'})
        elif msgt == 'log-done':
            # Logs were transferred
            if DONTPULL and DONTSTAY:
                raise SystemExit
    except (ValueError, TypeError, AttributeError) as e:
        try:
            lf = traceback.extract_tb(sys.exc_info()[2], 1)[-1]
        except Exception:
            lf = None
        log('FAULT reason=%r last-frame=%s' % (repr(e),
                                               instabot.format_log(lf)))
        return
def on_error(ws, exc):
    log('ERROR reason=%r' % repr(exc))
def on_close(ws):
    log('CLOSED')

class Scribe(instabot.Bot):
    NICKNAME = NICKNAME
    def __init__(self, url, nickname=None, **kwds):
        instabot.Bot.__init__(self, url, nickname, **kwds)
        self.scheduler = kwds['scheduler']
        self.db = kwds['db']
        self.dont_stay = kwds.get('dont_stay', False)
        self.dont_pull = kwds.get('dont_pull', False)
        self.ping_delay = kwds.get('ping_delay', PING_DELAY)
        self._cur_candidate = None
        self._logs_done = False
        self._ping_job = None
        self._ping_lock = threading.RLock()
    def on_open(self):
        instabot.Bot.on_open(self)
        log('OPENED')
    def on_message(self, rawmsg):
        log('MESSAGE content=%r' % (rawmsg,))
        instabot.Bot.on_message(self, rawmsg)
    def on_timeout(self, exc):
        self.log_exception('TIMEOUT', exc)
        instabot.Bot.on_timeout(self, exc)
    def on_error(self, exc):
        self.log_exception('ERROR', exc)
        instabot.Bot.on_error(self, exc)
    def on_close(self):
        instabot.Bot.on_close(self)
        log('CLOSED')
        with self._ping_lock:
            if self._ping_job is not None:
                self.scheduler.cancel(self._ping_job)
                self._ping_job = None
    def handle_identity(self, content, rawmsg):
        instabot.Bot.handle_identity(self, content, rawmsg)
        self.send_broadcast({'type': 'who'})
        if not self.dont_pull:
            self._logs_begin()
        self._send_ping(False)
        self.scheduler.forever = False
    def handle_joined(self, content, rawmsg):
        instabot.Bot.handle_joined(self, content, rawmsg)
        data = content['data']
        self._execute(self._process_nick, uid=data['id'], uuid=data['uuid'])
    def on_client_message(self, data, content, rawmsg):
        instabot.Bot.on_client_message(self, data, content, rawmsg)
        tp = data.get('type')
        if tp == 'nick':
            # Someone sharing their nick.
            self._execute(self._process_nick, uid=content['from'],
                          nick=data.get('nick'), uuid=data.get('uuid'))
        elif tp == 'post':
            # An individual message.
            data['id'] = content['id']
            data['from'] = content['from']
            data['timestamp'] = content['timestamp']
            self._execute(self._process_post, data=data)
        elif tp == 'log-query':
            # Someone interested in our logs.
            self._execute(self._process_log_query, uid=content['from'])
        elif tp == 'log-info':
            # Someone telling about their logs.
            if self.dont_pull: return
            self._execute(self._process_log_info, data=data,
                          uid=content['from'])
        elif tp == 'log-request':
            # Someone requesting some logs.
            self._execute(self._process_log_request, data=data,
                          uid=content['from'])
        elif tp == 'log':
            # Someone delivering logs.
            self._execute(self._process_log, data=data, uid=content['from'])
        elif tp == 'delete':
            # Message deletion request.
            self._execute(self._delete, ids=data.get('ids', ()))
        elif tp == 'log-inquiry':
            # Inquiry about whether we are done loading logs.
            if self._logs_done:
                self.send_unicast(content['from'], {'type': 'log-done'})
        elif tp == 'log-done':
            # Someone is done loading logs.
            if self.dont_stay and self.dont_pull:
                self.close()
    def send_raw(self, rawmsg, verbose=True):
        if verbose:
            log('SEND content=%r' % (rawmsg,))
        return instabot.Bot.send_raw(self, rawmsg)
    def process_logs(self, rawlogs, uuids):
        logs = []
        for e in rawlogs:
            if not isinstance(e, dict): continue
            logs.append(LogEntry(id=e.get('id'), parent=e.get('parent'),
                nick=e.get('nick'), timestamp=e.get('timestamp'),
                text=e.get('text'), **{'from': e.get('from')}))
        logs.sort()
        added = set(self.db.extend(logs))
        for e in logs:
            eid = e['id']
            if eid not in added: continue
            log('LOGPOST id=%r parent=%r from=%r nick=%r text=%r' %
                (eid, e['parent'], e['from'], e['nick'], e['text']))
        uuid_added = self.db.extend_uuid(uuids)
        uuid_added.sort()
        for k in uuid_added:
            log('LOGUUID id=%r uuid=%r' % (k, uuids[k]))
        return (added, uuid_added)
    def send_logs(self, peer, data):
        data.setdefault('type', 'log')
        ls = 'LOGSEND to=%r' % (peer,)
        if data['data']:
            ret = data['data']
            ls += ' log-from=%r log-to=%r log-count=%r' % (ret[0]['id'],
                ret[-1]['id'], len(ret))
        else:
            ls += ' log-count=0'
        if data.get('key'):
            ls += ' key=%r' % (data.get('key'),)
        log(ls)
        return self.send_unicast(peer, data, verbose=False)
    def log_exception(self, name, exc):
        try:
            frame = traceback.extract_tb(sys.exc_info()[2], 1)[-1]
        except Exception:
            frame = None
        log('%s reason=%r last-frame=%s' % (name, repr(exc),
                                            instabot.format_log(frame)))
    def _execute(self, func, *args, **kwds):
        self.scheduler.add_now(lambda: func(*args, **kwds))
    def _process_nick(self, uid, nick=None, uuid=None):
        if nick:
            if uuid:
                log('NICK id=%r uuid=%r nick=%r' % (uid, uuid, nick))
            else:
                log('NICK id=%r nick=%r' % (uid, nick))
        if uuid:
            if self.db.append_uuid(uid, uuid):
                log('UUID id=%r uuid=%r' % (uid, uuid))
    def _process_post(self, data):
        post = LogEntry(id=data.get('id'), parent=data.get('parent'),
                        nick=data.get('nick'), text=data.get('text'),
                        timestamp=data.get('timestamp'),
                        **{'from': data.get('from')})
        log('POST id=%r parent=%r from=%r nick=%r text=%r' %
            (post['id'], post['parent'], post['from'], post['nick'],
             post['text']))
        self.db.append(post)
    def _process_log_query(self, uid):
        bounds = self.db.bounds()
        if bounds[2] and uid != self.identity['id']:
            self.send_unicast(uid, {'type': 'log-info', 'from': bounds[0],
                'to': bounds[1], 'length': bounds[2]})
    def _process_log_info(self, data, uid):
        if not data.get('from'): return
        if (self._cur_candidate is None or
                data['from'] < self._cur_candidate['from']):
            data['reqto'] = self.db.bounds()[0]
            self._cur_candidate = data
            self.scheduler.add(1, lambda: self._send_request(data, uid))
    def _send_request(self, data, uid=None):
        if data is None or uid is None:
            self._logs_finish()
            return
        if self._cur_candidate is not data:
            return
        self.send_unicast(uid, {'type': 'log-request', 'to': data['reqto']})
    def _process_log_request(self, data, uid):
        logs = self.db.query(data.get('from'), data.get('to'),
                             data.get('amount'))
        reply = {'data': logs,
                 'uuids': self.db.query_uuid(ent['from'] for ent in logs)}
        for k in ('from', 'to', 'amount', 'key'):
            if data.get(k) is not None: reply[k] = data[k]
        self.send_logs(uid, reply)
    def _process_log(self, data, uid):
        rawlogs, uuids = data.get('data', []), data.get('uuids', {})
        res = self.process_logs(rawlogs, uuids)
        if not self.dont_pull:
            if res[0] or res[1]:
                self._logs_begin()
            else:
                self._logs_finish()
    def _delete(self, ids):
        for msg in self.db.delete(ids):
            log('DELETE id=%r parent=%r from=%r nick=%r text=%r' %
                (msg['id'], msg['parent'], msg['from'], msg['nick'],
                 msg['text']))
    def _logs_begin(self):
        self._cur_candidate = None
        self.send_broadcast({'type': 'log-query'})
        self.scheduler.add(1, lambda: self._send_request(None))
    def _logs_finish(self):
        self._logs_done = True
        self.send_broadcast({'type': 'log-done'})
        if self.dont_stay: self.close()
    def _send_ping(self, actually=True):
        if actually: self.send_seq({'type': 'ping'})
        with self._ping_lock:
            self._ping_job = self.scheduler.add(self.ping_delay,
                                                self._send_ping)

def test(url, **kwds):
    sched = instabot.EventScheduler()
    db = LogDBList()
    s = Scribe(url, 'Scribe (test)', scheduler=sched, db=db, **kwds)
    sched.add_now(s.start)
    sched.main()

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
    def run_push_logs():
        while push_logs:
            peer = push_logs.pop(0)
            log('LOGPUSH to=%r' % (peer,))
            send_logs(ws, peer)
        send_broadcast(ws, {'type': 'log-inquiry'})
    def run_ping():
        EVENTS.add(PING_DELAY, run_ping)
        send_seq(ws, {'type': 'ping'})
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
                elif arg == '--test':
                    test(next(it), dont_stay=DONTSTAY, dont_pull=DONTPULL,
                         ping_delay=10)
                    raise SystemExit
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
    LOGS.init()
    for fn in toread:
        log('READING file=%r maxlen=%r' % (fn, LOGS.capacity()))
        try:
            with openarg(fn) as f:
                logs, uuids = read_posts_ex(f, LOGS.capacity())
                LOGS.extend(logs)
                LOGS.extend_uuid(uuids)
                logs, uuids = None, None
        except IOError as e:
            log('ERROR reason=%r' % repr(e))
    log('LOGBOUNDS from=%r to=%r amount=%r' % LOGS.bounds())
    ws, reconnect = None, 0
    try:
        while 1:
            log('CONNECT url=%r' % addr)
            try:
                ws = instabot.BackgroundWebSocket(addr)
                ws.connect()
            except Exception as e:
                log('ERROR reason=%r' % repr(e))
                time.sleep(reconnect)
                reconnect += 1
                continue
            EVENTS.time = time.time
            EVENTS.sleep = ws.recv
            EVENTS.clear()
            EVENTS.add(0.5, run_push_logs)
            EVENTS.add(PING_DELAY, run_ping)
            on_open(ws)
            reconnect = 0
            while 1:
                try:
                    msg = EVENTS.run()
                    on_message(ws, msg)
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
            EVENTS.sleep = EVENTS._sleep
            on_close(ws)
            ws = None
            time.sleep(reconnect)
            reconnect += 1
    except (KeyboardInterrupt, SystemExit) as e:
        try:
            if isinstance(e, SystemExit):
                log('EXITING')
            else:
                log('INTERRUPTED')
            if ws: on_close(ws)
        except Exception:
            pass
    except Exception as e:
        log('CRASHED')
        sys.stderr.write('\n***CRASH*** at %s\n' %
            time.strftime('%Y-%m-%d %H:%M:%S Z', time.gmtime()))
        sys.stderr.flush()
        raise
    finally:
        LOGS.close()

if __name__ == '__main__': main()
