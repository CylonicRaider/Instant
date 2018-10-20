
# -*- coding: ascii -*-

# A PEP 342 generator-based coroutine framework.

import time
import heapq
import select

class Suspend:
    def apply(self, wake, executor, routine):
        raise NotImplementedError

class Exit(Suspend):
    def __init__(self, result):
        self.result = result

    def apply(self, wake, executor, routine):
        routine.close()
        executor._done(routine, self.result)

class Call(Suspend):
    def __init__(self, target):
        self.target = target

    def apply(self, wake, executor, routine):
        executor.add(self.target)
        executor.listen(self.target, wake)

class Sleep(Suspend):
    def __init__(self, waketime, absolute=False):
        if not absolute: waketime += time.time()
        self.waketime = waketime
        self.callbacks = []

    def __lt__(self, other):
        return self.waketime <  other.waketime
    def __le__(self, other):
        return self.waketime <= other.waketime
    def __eq__(self, other):
        return self.waketime == other.waketime
    def __ne__(self, other):
        return self.waketime != other.waketime
    def __ge__(self, other):
        return self.waketime >= other.waketime
    def __gt__(self, other):
        return self.waketime >  other.waketime

    def add_callback(self, callback):
        self.callbacks.append(callback)

    def done(self, value=None):
        callbacks, self.callbacks = self.callbacks, []
        for callback in callbacks:
            callback(value)

    def apply(self, wake, executor, routine):
        self.add_callback(wake)
        executor.add_sleep(self)

class Executor:
    def __init__(self):
        self.routines = {}
        self.suspended = set()
        self.listening = {}
        self.sleeps = []

    def add(self, routine, value=None):
        self.routines[routine] = value

    def remove(self, routine):
        self.suspended.discard(routine)
        return self.routines.pop(routine, None)

    def _suspend(self, routine):
        if routine not in self.routines:
            raise RuntimeError('Suspending not-running routine')
        del self.routines[routine]
        self.suspended.add(routine)

    def _wake(self, routine, value):
        if routine in self.routines:
            raise RuntimeError('Waking already-woken routine')
        self.suspended.discard(routine)
        self.routines[routine] = value

    def _done(self, routine, result=None):
        self.trigger(routine, result)
        self.remove(routine)

    def listen(self, event, callback):
        self.listening.setdefault(event, []).append(callback)

    def trigger(self, event, result=None):
        for cb in self.listening.pop(event, ()):
            cb(result)

    def add_sleep(self, sleep):
        heapq.heappush(self.sleeps, sleep)

    def finish_sleeps(self, now=None):
        if now is None: now = time.time()
        while self.sleeps and self.sleeps[0].waketime <= now:
            sleep = heapq.heappop(self.sleeps)
            sleep.done()

    def __call__(self):
        def make_wake(routine):
            return lambda value=None: self._wake(routine, value)
        while self.routines or self.suspended:
            for r, v in tuple(self.routines.items()):
                self.routines[r] = None
                try:
                    suspend = r.send(v)
                except StopIteration:
                    self._done(r)
                    continue
                if suspend is None:
                    continue
                if not suspend.apply(make_wake(r), self, r):
                    self._suspend(r)
            if not self.routines and self.sleeps:
                time.sleep(self.sleeps[0].waketime - time.time())
            self.finish_sleeps()

def run(routines):
    ex = Executor()
    for r in routines: ex.add(r)
    ex()
