
# -*- coding: ascii -*-

# A generator-based coroutine framework.

import select

def run(routines):
    while routines:
        for r in routines:
            try:
                result = r.next()
            except StopIteration:
                routines.remove(r)
