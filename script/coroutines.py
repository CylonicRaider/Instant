
# -*- coding: ascii -*-

# A generator-based coroutine framework.

import select

class Executor:
    def __init__(self):
        self.routines = {}

    def add(self, routine):
        self.routines[routine] = True

    def _remove(self, routine):
        self.routines.pop(routine, None)

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
