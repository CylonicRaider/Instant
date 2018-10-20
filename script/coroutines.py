
# -*- coding: ascii -*-

# A PEP 342 generator-based coroutine framework.

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

class Executor:
    def __init__(self):
        self.routines = {}
        self.suspended = set()
        self.listening = {}

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

def run(routines):
    ex = Executor()
    for r in routines: ex.add(r)
    ex()
