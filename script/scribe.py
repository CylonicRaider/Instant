#!/usr/bin/env python3
# -*- coding: ascii -*-

"""
A log-keeping bot for Instant.
"""

import sys, os, re, time
import threading
import bisect
import contextlib
import signal
import json
import sqlite3

import websocket_server

import instabot

NICKNAME = 'Scribe'
VERSION = instabot.VERSION
MAXLEN = None

PING_DELAY = 3600 # 1 h
MAX_PINGS = 3

def parse_version(s):
    if isinstance(s, float): s = str(s)
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
    def __init__(self, maxlen=None):
        if maxlen is None: maxlen = MAXLEN
        self.maxlen = maxlen
    def __enter__(self):
        self.init()
        return self
    def __exit__(self, *exc_info):
        self.close()
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

class LogDBNull(LogDB):
    def bounds(self):
        return (None, None, None)
    def get(self, index):
        return None
    def query(self, lfrom=None, lto=None, amount=None):
        return []
    def extend(self, entries):
        return []
    def delete(self, ids):
        return []
    def append_uuid(self, uid, uuid):
        return False
    def get_uuid(self, uid):
        return False
    def query_uuid(self, ids=None):
        return []

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
    def __init__(self, maxlen=None):
        LogDB.__init__(self, maxlen)
        self.data = []
        self.uuids = {}
        self._uuid_list = []
    def bounds(self):
        if not self.data:
            return (None, None, None)
        else:
            return (self.data[0]['id'], self.data[-1]['id'], len(self.data))
    def get(self, index):
        try:
            return self.data[index]
        except IndexError:
            return None
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
        if self.maxlen is not None and len(self._uuid_list) > 4 * self.maxlen:
            # First, keep the most recent maxlen UUIDs.
            keep = set()
            for uid in reversed(self._uuid_list):
                if len(keep) >= self.maxlen: break
                keep.add(uid)
            # Also, retain UUIDs referenced by logs.
            keep.update(entry['from'] for entry in self.data)
            # Extract the last appearances of the entries to be retained in
            # their proper order.
            new_uuid_list, seen = [], set()
            for uid in reversed(self._uuid_list):
                if uid in seen or uid not in keep: continue
                seen.add(uid)
                new_uuid_list.append(uid)
            # Save the transformed usage list and trim old UUID mappings.
            self._uuid_list[:] = reversed(new_uuid_list)
            for uid in tuple(self.uuids):
                if uid in seen: continue
                del self.uuids[uid]
        return ret
    def get_uuid(self, uid):
        return self.uuids.get(uid)
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
    def __init__(self, filename, maxlen=None):
        LogDB.__init__(self, maxlen)
        self.filename = filename
    def init(self):
        self.conn = sqlite3.connect(self.filename)
        # Allow tuning DB performance.
        sync = os.environ.get('SCRIBE_DB_SYNC', '')
        if re.match(r'^[A-Za-z0-9]+$', sync):
            self.conn.execute('PRAGMA synchronous = ' + sync)
        # Create cursor.
        self.cursor = self.conn.cursor()
        # The REFERENCES is not enforced to allow "stray" messages to
        # be preserved.
        self.cursor.execute('CREATE TABLE IF NOT EXISTS logs ('
                                'msgid INTEGER PRIMARY KEY, '
                                'parent INTEGER, ' # REFERENCES msgid
                                'sender INTEGER, '
                                'nick TEXT, '
                                'text TEXT'
                            ')')
        self.cursor.execute('CREATE TABLE IF NOT EXISTS uuid ('
                                'user INTEGER PRIMARY KEY, '
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
            stmt = ('SELECT * FROM logs WHERE msgid BETWEEN ? AND ? '
                    'ORDER BY msgid ASC', (fromkey, tokey))
        elif fromkey is not None:
            if amount is not None:
                stmt = ('SELECT * FROM logs WHERE msgid >= ? '
                        'ORDER BY msgid ASC LIMIT ?', (fromkey, amount))
            else:
                stmt = ('SELECT * FROM logs WHERE msgid >= ? '
                        'ORDER BY msgid ASC', (fromkey,))
        elif tokey is not None:
            if amount is not None:
                stmt = ('SELECT * FROM logs WHERE msgid <= ? '
                        'ORDER BY msgid DESC LIMIT ?', (tokey, amount))
            else:
                stmt = ('SELECT * FROM logs WHERE msgid <= ? '
                        'ORDER BY msgid DESC', (tokey,))
            flip = True
        elif amount is not None:
            stmt = ('SELECT * FROM logs ORDER BY msgid DESC '
                    'LIMIT ?', (amount,))
            flip = True
        else:
            stmt = ('SELECT * FROM logs ORDER BY msgid',)
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
        added = []
        for k in mapping.keys():
            self.cursor.execute('SELECT 1 FROM uuid WHERE user = ?',
                                (self.make_strkey(k),))
            if not self.cursor.fetchone(): added.append(k)
        self.cursor.executemany('INSERT OR REPLACE INTO uuid (user, uuid) '
            'VALUES (?, ?)',
            ((self.make_strkey(k), v) for k, v in mapping.items()))
        self.conn.commit()
        return added
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
        self.conn.close()

def read_posts_ex(logger, maxlen=None, filt=None):
    def truncate(ret, uuids):
        delset, kset = set(dels), set(sorted(uuids)[-maxlen:])
        cur_ids = set(i['id'] for i in ret)
        dels[:] = [d for d in dels if d not in cur_ids]
        ret = [i for i in ret if i['id'] not in delset]
        ret.sort()
        ret = ret[-maxlen:]
        kset.update(i['from'] for i in ret)
        uuids = dict((k, v) for k, v in uuids.items() if k in kset)
        seen.intersection_update(i['id'] for i in ret)
        return (ret, uuids)
    def prune(ret, uuids):
        delset = set(dels)
        ret = [i for i in ret if i['id'] not in delset]
        return (ret, uuids)
    allow_tags = set(('SCRIBE', 'POST', 'LOGPOST', 'MESSAGE', 'DELETE',
                      'UUID'))
    cver, froms, dels, ret, uuids, seen = (), {}, [], [], {}, set()
    n = 0
    for ts, tag, values in logger.read_back(allow_tags.__contains__):
        if tag == 'SCRIBE':
            cver = parse_version(values.get('version'))
            if cver >= (1, 2): allow_tags.discard('MESSAGE')
            continue
        if tag in ('POST', 'LOGPOST'):
            pass
        elif tag == 'MESSAGE':
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
        if 'id' not in values or values['id'] in seen:
            continue
        seen.add(values['id'])
        values = LogEntry(values)
        if 'timestamp' not in values:
            values['timestamp'] = LogEntry.derive_timestamp(values['id'])
        if 'text' not in values and 'content' in values:
            values['text'] = values['content']
            del values['content']
        if 'from' not in values and values['id'] in froms:
            values['from'] = froms.pop(values['id'])
        if filt and not filt(values, {'uuid': uuids.get(values['id'])}):
            continue
        ret.append(values)
        if maxlen is not None and len(ret) >= 2 * maxlen:
            ret, uuids = truncate(ret, uuids)
    if maxlen is not None:
        ret, uuids = truncate(ret, uuids)
    else:
        ret, uuids = prune(ret, uuids)
    ret.sort(key=lambda x: x['id'])
    return (ret, uuids)
def read_posts(logger, maxlen=None, filt=None):
    return read_posts_ex(logger, maxlen, filt)[0]

class Scribe(instabot.Bot):
    NICKNAME = NICKNAME
    def __init__(self, url, nickname=Ellipsis, **kwds):
        instabot.Bot.__init__(self, url, nickname, **kwds)
        self.scheduler = kwds['scheduler']
        self.db = kwds['db']
        self.dont_stay = kwds.get('dont_stay', False)
        self.dont_pull = kwds.get('dont_pull', False)
        self.ping_delay = kwds.get('ping_delay', PING_DELAY)
        self.max_pings = kwds.get('max_pings', MAX_PINGS)
        self.push_logs = kwds.get('push_logs', [])
        self.reconnect = True
        self._selecting_candidate = False
        self._cur_candidate = None
        self._already_loaded = {}
        self._logs_done = False
        self._ping_job = None
        self._last_pong = None
        self._ping_lock = threading.RLock()
    def connect(self):
        self.scheduler.set_forever(True)
        return instabot.Bot.connect(self)
    def on_open(self):
        instabot.Bot.on_open(self)
        self._last_pong = None
    def on_message(self, rawmsg):
        self.logger.log('MESSAGE content=%r' % (rawmsg,))
        instabot.Bot.on_message(self, rawmsg)
    def on_connection_error(self, exc):
        self.logger.log_exception('ERROR', exc)
    def on_close(self, final):
        instabot.Bot.on_close(self, final)
        with self._ping_lock:
            if self._ping_job is not None:
                self.scheduler.cancel(self._ping_job)
                self._ping_job = None
        self.scheduler.set_forever(False)
    def handle_pong(self, content, rawmsg):
        instabot.Bot.handle_pong(self, content, rawmsg)
        self._last_pong = time.time()
    def handle_identity(self, content, rawmsg):
        self._send_ping()
        instabot.Bot.handle_identity(self, content, rawmsg)
        self.send_seq({'type': 'who'})
        self.send_broadcast({'type': 'who'})
        self._execute(self._push_logs)
        if not self.dont_pull:
            self._logs_begin()
        self.scheduler.set_forever(False)
    def handle_who(self, content, rawmsg):
        instabot.Bot.handle_who(self, content, rawmsg)
        data = content['data']
        self._execute(self._process_who, data=data)
    def handle_joined(self, content, rawmsg):
        instabot.Bot.handle_joined(self, content, rawmsg)
        data = content['data']
        self._execute(self._process_joined, uid=data['id'], uuid=data['uuid'])
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
            self._execute(self._delete, ids=data.get('ids', ()),
                          cause=content['from'])
        elif tp == 'log-inquiry':
            # Inquiry about whether we are done loading logs.
            if self._logs_done:
                self.send_unicast(content['from'], {'type': 'log-done'})
        elif tp == 'log-done':
            # Someone is done loading logs.
            if self.dont_stay and self.dont_pull:
                self.reconnect = False
                self.close()
        elif tp == 'privmsg':
            # Someone is PM-ing us.
            # Just log it.
            data['id'] = content['id']
            data['from'] = content['from']
            data['timestamp'] = content['timestamp']
            self._process_pm(data)
    def send_raw(self, rawmsg, verbose=True):
        if verbose:
            self.logger.log('SEND content=%r' % (rawmsg,))
        return instabot.Bot.send_raw(self, rawmsg)
    def run(self, *args, **kwds):
        try:
            instabot.Bot.run(self, *args, **kwds)
        except Exception as exc:
            self.logger.log_exception('CRASHED', exc)
            sys.stderr.write('\n***EXCEPTION*** at %s\n' %
                time.strftime('%Y-%m-%d %H:%M:%S Z', time.gmtime()))
            sys.stderr.flush()
            raise
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
            self.logger.log(
                'LOGPOST id=%r parent=%r from=%r nick=%r text=%r' %
                    (eid, e['parent'], e['from'], e['nick'], e['text']))
        uuid_added = self.db.extend_uuid(uuids)
        uuid_added.sort()
        for k in uuid_added:
            self.logger.log('LOGUUID id=%r uuid=%r' % (k, uuids[k]))
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
        self.logger.log(ls)
        return self.send_unicast(peer, data, verbose=False)
    def _execute(self, func, *args, **kwds):
        self.scheduler.add_now(lambda: func(*args, **kwds))
    def _process_who(self, data):
        for uid, info in data.items():
            self._process_nick(uid, uuid=info['uuid'])
    def _process_joined(self, uid, uuid=None):
        self._process_nick(uid, uuid=uuid)
        if self._selecting_candidate:
            self.send_unicast(uid, {'type': 'log-query'})
    def _process_nick(self, uid, nick=None, uuid=None):
        if nick:
            if uuid:
                self.logger.log('NICK id=%r uuid=%r nick=%r' % (uid, uuid,
                                                                nick))
            else:
                self.logger.log('NICK id=%r nick=%r' % (uid, nick))
        if uuid:
            if self.db.append_uuid(uid, uuid):
                self.logger.log('UUID id=%r uuid=%r' % (uid, uuid))
    def _process_post(self, data):
        post = LogEntry(id=data.get('id'), parent=data.get('parent'),
                        nick=data.get('nick'), text=data.get('text'),
                        timestamp=data.get('timestamp'),
                        **{'from': data.get('from')})
        self.logger.log('POST id=%r parent=%r from=%r nick=%r text=%r' %
                        (post['id'], post['parent'], post['from'],
                         post['nick'], post['text']))
        self.db.append(post)
    def _process_log_query(self, uid):
        bounds = self.db.bounds()
        if bounds[2] and uid != self.identity['id']:
            self.send_unicast(uid, {'type': 'log-info', 'from': bounds[0],
                'to': bounds[1], 'length': bounds[2]})
    def _process_log_info(self, data, uid):
        if not data.get('from') or uid == self.identity['id']:
            return
        if (self._cur_candidate is None or
                data['from'] < self._cur_candidate['from']):
            dbfrom = self.db.bounds()[0]
            if dbfrom is not None and data['from'] < dbfrom:
                data['reqto'] = dbfrom
            elif uid in self._already_loaded:
                data['reqto'] = self._already_loaded[uid]
            else:
                data['reqto'] = data['to']
            self._cur_candidate = data
            self.scheduler.add(1, lambda: self._send_request(data, uid))
    def _send_request(self, data, uid=None):
        if self._cur_candidate is not data:
            return
        self._selecting_candidate = False
        if data is None or uid is None:
            self._logs_finish()
            return
        self.send_unicast(uid, {'type': 'log-request', 'to': data['reqto']})
    def _process_log_request(self, data, uid):
        logs = self.db.query(data.get('from'), data.get('to'),
                             data.get('length'))
        response = {'data': logs,
                    'uuids': self.db.query_uuid(ent['from'] for ent in logs)}
        if data.get('key') is not None: response['key'] = data['key']
        self.send_logs(uid, response)
    def _process_log(self, data, uid):
        rawlogs, uuids = data.get('data', []), data.get('uuids', {})
        for k, v in data.get('users', {}).items():
            u = v.get('uuid')
            if u: uuids[k] = u
        res = self.process_logs(rawlogs, uuids)
        if not self.dont_pull:
            if rawlogs:
                self._already_loaded[uid] = min(i['id'] for i in rawlogs)
            if res[0] or res[1]:
                self._logs_begin()
            else:
                self._logs_finish()
    def _delete(self, ids, cause=None):
        handled = set()
        for msg in self.db.delete(ids):
            handled.add(msg['id'])
            self.logger.log(
                'DELETE by=%r id=%r parent=%r from=%r nick=%r text=%r' %
                    (cause, msg['id'], msg['parent'], msg['from'],
                     msg['nick'], msg['text']))
        for i in ids:
            if i in handled: continue
            self.logger.log('DELETE by=%r id=%r' % (cause, i))
    def _process_pm(self, data):
        self.logger.log(
            'PRIVMSG id=%r parent=%r from=%r nick=%r subject=%r text=%r' %
                (data['id'], data.get('parent'), data['from'],
                 data.get('nick'), data.get('subject'), data.get('text')))
    def _push_logs(self, peer=None):
        if peer is None:
            if not self.push_logs: return
            peer = self.push_logs.pop(0)
            do_again = bool(self.push_logs)
            inquire = (not do_again)
        else:
            do_again, inquire = False, False
        bounds = self.db.bounds()
        data = self.db.query(bounds[0], bounds[1])
        uuids = self.db.query_uuid(ent['from'] for ent in data)
        self.send_logs(peer, {'data': data, 'uuids': uuids})
        if do_again:
            self._execute(self._push_logs)
        elif inquire:
            self.send_broadcast({'type': 'log-inquiry'})
    def _logs_begin(self):
        self._selecting_candidate = True
        self._cur_candidate = None
        self.send_broadcast({'type': 'log-query'})
        self.scheduler.add(1, lambda: self._send_request(None))
    def _logs_finish(self):
        if not self._logs_done:
            self.send_broadcast({'type': 'log-done'})
        self._logs_done = True
        if self.dont_stay:
            self.reconnect = False
            self.close()
    def _send_ping(self):
        now = time.time()
        if (self._last_pong is not None and now >= self._last_pong +
                self.max_pings * self.ping_delay):
            self.close()
            return
        self.send_seq({'type': 'ping',
                       'next': (now + self.ping_delay) * 1000})
        with self._ping_lock:
            self._ping_job = self.scheduler.add(self.ping_delay,
                                                self._send_ping)

def main():
    @contextlib.contextmanager
    def openarg(fname):
        if fname == '-':
            yield sys.stdin
        else:
            with open(fname) as f:
                yield f
    def install_sighandler(signum, callback):
        try:
            signal.signal(signal.SIGINT, callback)
        except Exception as e:
            logger.log_exception('WARNING', e)
    def interrupt(signum, frame):
        raise SystemExit
    def handle_crash(exc):
        logger.log_exception('CRASHED', e)
        sys.stderr.write('\n***CRASH*** at %s\n' %
            time.strftime('%Y-%m-%d %H:%M:%S Z', time.gmtime()))
        sys.stderr.flush()
        raise
    b = instabot.CmdlineBotBuilder(Scribe, NICKNAME, None)
    p = b.make_parser(sys.argv[0],
                      desc='An Instant bot storing room logs.')
    p.option('ping-delay', PING_DELAY, type=float, varname='ping_delay',
             placeholder='<seconds>',
             help='Time in between keep-alive pings')
    p.option('maxlen', MAXLEN, type=int,
             help='Maximum amount of logs to deliver')
    p.option('msgdb', placeholder='<file>', default=Ellipsis,
             help='SQLite database file for messages')
    p.flag_ex('no-msgdb', None, 'msgdb',
              help='Do not store messages at all')
    p.option('read-file', placeholder='<file>',
             help='Parse log file for messages')
    p.option('read-rotate', placeholder='<time>[:<compress>]',
             help='Assume the file has been rotated as given')
    p.option('push-logs', [], accum=True, varname='push_logs',
             placeholder='<id>', help='Send logs to given ID without asking')
    p.flag('dont-stay', varname='dont_stay',
           help='Exit after collecting logs')
    p.flag('dont-pull', varname='dont_pull', help='Do not collect logs')
    b.parse(sys.argv[1:])
    b.add_args('ping_delay', 'push_logs', 'dont_stay', 'dont_pull')
    maxlen, msgdb_file = b.get_args('maxlen', 'msgdb')
    read_file, read_rotate = b.get_args('read-file', 'read-rotate')
    logger = b.kwds.get('logger', instabot.DEFAULT_LOGGER)
    logger.log('SCRIBE version=%s' % VERSION)
    install_sighandler(signal.SIGINT, interrupt)
    install_sighandler(signal.SIGTERM, interrupt)
    logger.log('OPENING file=%r maxlen=%r' % (msgdb_file, maxlen))
    try:
        if msgdb_file is None:
            msgdb = LogDBNull(maxlen)
        elif msgdb_file is Ellipsis:
            msgdb = LogDBList(maxlen)
        else:
            msgdb = LogDBSQLite(msgdb_file, maxlen)
        msgdb.init()
    except Exception as e:
        handle_crash(e)
    if read_file is not None:
        logger.log('READING file=%r rotation=%r maxlen=%r' % (read_file,
            read_rotate, msgdb.capacity()))
        try:
            with b.build_logger(read_file, read_rotate) as l:
                logs, uuids = read_posts_ex(l, msgdb.capacity())
                msgdb.extend(logs)
                msgdb.extend_uuid(uuids)
                logs, uuids = None, None
        except IOError as e:
            logger.log('ERROR reason=%r' % repr(e))
    logger.log('LOGBOUNDS from=%r to=%r amount=%r' % msgdb.bounds())
    if b.get_args('url') is None:
        logger.log('EXITING')
        return
    sched = instabot.EventScheduler()
    bot = b(scheduler=sched, db=msgdb, keepalive=False, logger=logger)
    thr = None
    try:
        while 1:
            sched.set_forever(True)
            canceller = instabot.Canceller()
            thr = bot.start(canceller)
            try:
                sched.run()
            except websocket_server.ConnectionClosedError:
                pass
            sched.clear()
            canceller.cancel()
            ws = bot.ws
            if ws: ws.close_now()
            bot.close(False)
            thr.join(1)
            if not bot.reconnect: break
            time.sleep(1)
    except (KeyboardInterrupt, SystemExit) as e:
        bot.close()
        if isinstance(e, SystemExit):
            logger.log('EXITING')
        else:
            logger.log('INTERRUPTED')
    except Exception as e:
        handle_crash(e)
    finally:
        if thr: thr.join(1)
        msgdb.close()

if __name__ == '__main__': main()
