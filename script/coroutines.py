
# -*- coding: ascii -*-

# A PEP 342 generator-based coroutine framework.

import select

class Suspend:
    def apply(self, executor, routine):
        raise NotImplementedError

class Call(Suspend):
    def __init__(self, target):
        self.target = target

    def apply(self, executor, routine):
        executor.add(self.target)
        executor.suspend(routine, self.target)

class Executor:
    def __init__(self):
        self.routines = set()
        self.suspended = {}

    def add(self, routine):
        self.routines.add(routine)

    def remove(self, routine):
        self.routines.discard(routine)

    def suspend(self, routine, reason):
        self.routines.discard(routine)
        self.suspended.setdefault(reason, []).append(routine)

    def wake(self, reason):
        self.routines.update(self.suspended.pop(reason, ()))

    def _done(self, routine):
        self.wake(routine)
        self.remove(routine)

    def __call__(self):
        while self.routines or self.suspended:
            for routine in tuple(self.routines):
                try:
                    suspend = routine.next()
                except StopIteration:
                    self._done(routine)
                    continue
                if suspend is not None:
                    suspend.apply(self, routine)

def run(routines):
    ex = Executor()
    for r in routines: ex.add(r)
    ex()
