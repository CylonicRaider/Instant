
# -*- coding: ascii -*-

# A PEP 342 generator-based coroutine framework.

import time
import heapq
import select

class Suspend(object):
    def apply(self, wake, executor, routine):
        raise NotImplementedError

class ControlSuspend(Suspend): pass

class Exit(ControlSuspend):
    def __init__(self, result):
        self.result = result

    def apply(self, wake, executor, routine):
        routine.close()
        executor._done(routine, self.result)

class Call(ControlSuspend):
    def __init__(self, target):
        self.target = target

    def apply(self, wake, executor, routine):
        executor.add(self.target)
        executor.listen(self.target, wake)

class Sleep(Suspend):
    def __init__(self, waketime, absolute=False):
        if not absolute: waketime += time.time()
        self.waketime = waketime

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

    def apply(self, wake, executor, routine):
        executor.listen(self, wake)
        executor.add_sleep(self)

class IOSuspend(Suspend):
    SELECT_MODES = {'r': 0, 'w': 1, 'x': 2}

    def __init__(self, file, mode):
        if mode not in self.SELECT_MODES:
            raise ValueError('Invalid I/O mode: %r' % (mode,))
        self.file = file
        self.mode = mode

    def apply(self, wake, executor, routine):
        executor.listen(self.file, wake)
        executor.add_select(self.file, self.SELECT_MODES[self.mode])

class Executor:
    def __init__(self):
        self.routines = {}
        self.suspended = set()
        self.listening = {}
        self.sleeps = []
        self.selectfiles = ([], [], [])

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

    def _finish_sleeps(self, now=None):
        if now is None: now = time.time()
        while self.sleeps and self.sleeps[0].waketime <= now:
            sleep = heapq.heappop(self.sleeps)
            self.trigger(sleep)

    def add_select(self, file, index):
        l = self.selectfiles[index]
        if file not in l: l.append(file)

    def _done_select(self, readable, writable, exceptable):
        def clear(lst, rem): lst[:] = [f in lst if f not in rem]
        if readable: clear(self.selectfiles[0], frozenset(readable))
        if writable: clear(self.selectfiles[1], frozenset(writable))
        if exceptable: clear(self.selectfiles[2], frozenset(exceptable))
        for f in readable: self.trigger(f, 'r')
        for f in writable: self.trigger(f, 'w')
        for f in exceptable: self.trigger(f, 'x')

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
            if any(self.selectfiles):
                if self.routines or not self.sleeps:
                    timeout = 0
                else:
                    timeout = self.sleeps[0].waketime - time.time()
                result = select.select(self.selectfiles[0],
                                       self.selectfiles[1],
                                       self.selectfiles[2],
                                       timeout)
                self._done_select(*result)
            elif self.sleeps and not self.routines:
                time.sleep(self.sleeps[0].waketime - time.time())
            self._finish_sleeps()

def run(routines):
    ex = Executor()
    for r in routines: ex.add(r)
    ex()
