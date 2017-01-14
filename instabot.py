# -*- coding: ascii -*-

import time
import heapq
import json
import threading

import websocket

try:
    from queue import Queue, Empty as QueueEmpty
except ImportError:
    from Queue import Queue, Empty as QueueEmpty

VERSION = 'v1.4'

class EventScheduler(object):
    @staticmethod
    def sleep(delay):
        if delay is not None: time.sleep(delay)
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
    def __init__(self, time=None, sleep=None):
        if time is None: time = self._time
        if sleep is None: sleep = self._sleep
        self.pending = []
        self.time = time
        self.sleep = sleep
        self.cond = threading.Condition()
    def __enter__(self):
        return self.cond.__enter__()
    def __exit__(self, *args):
        return self.cond.__exit__(*args)
    def _time(self):
        return time.time()
    def _sleep(self, delay):
        return self.cond.wait(delay)
    def add_abs(self, timestamp, callback):
        with self:
            heapq.heappush(self.pending, self.Event(timestamp, callback))
            self.cond.notify()
    def add(self, delay, callback):
        return self.add_abs(self.time() + delay, callback)
    def add_now(self, callback):
        return self.add_abs(self.time(), callback)
    def clear(self):
        with self:
            self.pending[:] = []
            self.cond.notify()
    def run(self):
        wait = None
        while 1:
            with self:
                if not self.pending: break
                now = self.time()
                head = self.pending[0]
                if head.time > now:
                    wait = head.time - now
                    break
                heapq.heappop(self.pending)
            head()
        return self.sleep(wait)
    def main(self):
        while self.run(): pass

class BackgroundWebSocket(object):
    def __init__(self, url):
        self.url = url
        self.queue = Queue()
        self.ws = None
    def connect(self):
        self.ws = websocket.create_connection(self.url)
        thr = threading.Thread(target=self._reader)
        thr.setDaemon(True)
        thr.start()
    def _reader(self):
        try:
            while 1:
                msg = self.ws.recv()
                if not self.on_message(msg):
                    self.queue.put(msg)
        except BaseException as e:
            if not self.on_error(e):
                self.queue.put(e)
    def on_message(self, msg):
        return False
    def on_error(self, exc):
        return False
    def recv(self, timeout=None):
        try:
            ret = self.queue.get(timeout=timeout)
        except QueueEmpty:
            raise websocket.WebSocketTimeoutException
        if isinstance(ret, BaseException): raise ret
        return ret
    def send(self, datum):
        self.ws.send(datum)
    def close(self):
        self.ws.close()

class AtomicSequence(object):
    def __init__(self):
        self.value = 0
        self._lock = threading.Lock()
    def __call__(self):
        with self._lock:
            ret = self.value
            self.value += 1
        return ret

class Bot(object):
    NICKNAME = None
    def __init__(self, url, nickname=None):
        if nickname is None: nickname = self.NICKNAME
        self.url = url
        self.nickname = nickname
        self.ws = None
        self.sequence = AtomicSequence()
        self.identity = None
    def connect(self):
        self.ws = websocket.create_connection(url)
    def on_open(self):
        if self.nickname is not None:
            self.send_broadcast({'type': 'nick', 'nick': self.nickname})
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
    def on_error(self, exc):
        raise exc
    def on_close(self):
        pass
    def handle_identity(self, content, rawmsg):
        self.identity = content['data']
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
    def send_raw(self, rawmsg):
        self.ws.send(rawmsg)
    def send_seq(self, content):
        seq = self.sequence()
        content['seq'] = seq
        self.send_raw(json.dumps(content, separators=(',', ':')))
        return seq
    def send_unicast(self, dest, data):
        return self.send_seq({'type': 'unicast', 'to': dest, 'data': data})
    def send_broadcast(self, data):
        return self.send_seq({'type': 'broadcast', 'data': data})
    def close(self):
        try:
            self.ws.close()
        except Exception as exc:
            self.on_error(exc)
        finally:
            self.on_close()
    def run(self):
        try:
            self.connect()
            while 1:
                rawmsg = self.ws.recv()
                if rawmsg is None: break
                self.on_message(rawmsg)
        except Exception as exc:
            self.on_error(exc)
        finally:
            try:
                self.ws.close()
            except Exception as exc:
                self.on_error(exc)
            self.on_close()
    def main(self):
        thr = threading.Thread(target=self.run)
        thr.setDaemon(True)
        thr.start()
        return thr
