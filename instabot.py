# -*- coding: ascii -*-

import time, heapq, threading

import websocket

try:
    from queue import Queue, Empty as QueueEmpty
except ImportError:
    from Queue import Queue, Empty as QueueEmpty

VERSION = 'v1.4'

class EventScheduler:
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

class BackgroundWebSocket:
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
                self.queue.put(self.ws.recv())
        except BaseException as e:
            self.queue.put(e)
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

class AtomicSequence:
    def __init__(self):
        self.value = 0
        self._lock = threading.Lock()
    def __call__(self):
        with self._lock:
            ret = self.value
            self.value += 1
        return ret
