# -*- coding: ascii -*-

import time, heapq, threading

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
