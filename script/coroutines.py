
# -*- coding: ascii -*-

# A PEP 342 generator-based coroutine framework.

import select

class Executor:
    def __init__(self):
        self.routines = set()

    def add(self, routine):
        self.routines.add(routine)

    def _remove(self, routine):
        self.routines.discard(routine)

    def __call__(self):
        while self.routines:
            for r in tuple(self.routines):
                try:
                    r.next()
                except StopIteration:
                    self._remove(r)

def run(routines):
    ex = Executor()
    for r in routines: ex.add(r)
    r()
