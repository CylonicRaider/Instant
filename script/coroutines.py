
# -*- coding: ascii -*-

# A PEP 342 generator-based coroutine framework.

import os, time
import heapq
import signal, select

class ProcessTag(object):
    def __init__(self, pid):
        self.pid = pid

    def __hash__(self):
        return hash(self.pid)

    def __eq__(self, other):
        return isinstance(other, ProcessTag) and other.pid == self.pid

    def __ne__(self, other):
        return not (self == other)

class Suspend(object):
    def apply(self, wake, executor, routine):
        raise NotImplementedError

    def cancel(self):
        raise TypeError('Cannot cancel %s suspend' % self.__class__.__name__)

class CombinationSuspend(Suspend):
    def __init__(self, children):
        self.children = children

    def cancel(self):
        for c in self.children:
            c.cancel()

class All(CombinationSuspend):
    def __init__(self, *suspends):
        CombinationSuspend.__init__(self, suspends)
        self.result = [None] * len(suspends)
        self._finished = [False] * len(suspends)
        self._finishedCount = 0

    def _finish(self, index, value, callback):
        if self._finished[index]:
            raise RuntimeError('Attempting to wake a coroutine more than '
                'once')
        self.result[index] = value
        self._finished[index] = True
        self._finishedCount += 1
        if self._finishedCount == len(self.children):
            callback(self.result)

    def apply(self, wake, executor, routine):
        def make_wake(index):
            return lambda value: self._finish(index, value, wake)
        for n, s in enumerate(self.children):
            s.apply(make_wake(n), executor, routine)

    def cancel(self):
        self._finishedCount = -1
        CombinationSuspend.cancel(self)

class Any(CombinationSuspend):
    def __init__(self, *suspends):
        CombinationSuspend.__init__(self, suspends)
        self._do_wake = True

    def _wake(self, suspend, value, callback):
        if not self._do_wake: return
        self._do_wake = False
        callback((suspend, value))
        for s in self.children:
            if s is not suspend:
                s.cancel()

    def apply(self, wake, executor, routine):
        def make_wake(suspend):
            return lambda value: self._wake(suspend, value, wake)
        for s in self.children:
            if not self._do_wake: break
            s.apply(make_wake(s), executor, routine)

    def cancel(self):
        self._do_wake = False
        CombinationSuspend.cancel(self)

class Selector(CombinationSuspend):
    def __init__(self, *suspends):
        CombinationSuspend.__init__(self, suspends)
        self._applied = False
        self._finished = 0
        self._pending = {}
        self._callback = None

    def has_pending(self):
        return (self._finished < len(self.children))

    def _finish(self, item):
        if self._finished >= len(self.children):
            raise RuntimeError('Tried to wake coroutine more than once')
        self._callback(item)
        self._callback = None
        self._finished += 1

    def _wake(self, suspend, value):
        if self._callback is None:
            if suspend in self._pending:
                raise RuntimeError('Trying to wake coroutine more than once')
            self._pending[suspend] = value
        else:
            self._finish((suspend, value))

    def apply(self, wake, executor, routine):
        def make_wake(suspend):
            return lambda value: self._wake(suspend, value)
        if not self._applied:
            for s in self.children:
                s.apply(make_wake(s), executor, routine)
            self._applied = True
        if self._finished == len(self.children):
            wake((None, None))
            return
        self._callback = wake
        if self._pending:
            self._finish(self._pending.popitem())

    def cancel(self):
        self._applied = True
        self._finished = len(self.children)
        self._pending.clear()
        self._callback = None
        CombinationSuspend.cancel(self)

class ControlSuspend(Suspend): pass

class InstantSuspend(ControlSuspend):
    def __init__(self, value=None):
        self.value = value

    def apply(self, wake, executor, routine):
        wake(self.value)

class Trigger(InstantSuspend):
    def __init__(self, *triggers, **kwds):
        InstantSuspend.__init__(self, **kwds)
        self.triggers = triggers

    def apply(self, wake, executor, routine):
        for tag, value in self.triggers:
            executor.trigger(tag, value)
        InstantSuspend.apply(self, wake, executor, routine)

class Exit(ControlSuspend):
    def __init__(self, result):
        self.result = result

    def apply(self, wake, executor, routine):
        routine.close()
        executor._done(routine, self.result)

class Join(ControlSuspend):
    def __init__(self, target):
        self.target = target

    def apply(self, wake, executor, routine):
        executor.listen(self.target, wake)

class Spawn(ControlSuspend):
    def __init__(self, target, daemon=False):
        self.target = target
        self.daemon = daemon

    def apply(self, wake, executor, routine):
        executor.add(self.target, daemon=self.daemon)
        wake(None)

class Call(ControlSuspend):
    def __init__(self, target, daemon=False):
        self.target = target
        self.daemon = daemon

    def apply(self, wake, executor, routine):
        executor.add(self.target, daemon=self.daemon)
        executor.listen(self.target, wake)

class Sleep(Suspend):
    def __init__(self, waketime, absolute=False):
        if not absolute: waketime += time.time()
        self.waketime = waketime
        self.cancelled = False

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

    def cancel(self):
        self.cancelled = True

    def apply(self, wake, executor, routine):
        def inner_wake(value):
            if not self.cancelled:
                wake(value)
        executor.listen(self, inner_wake)
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

class ReadFile(IOSuspend):
    def __init__(self, readfile, length, selectfile=None):
        if selectfile is None: selectfile = readfile
        IOSuspend.__init__(self, selectfile, 'r')
        self.readfile = readfile
        self.length = length
        self.cancelled = False

    def cancel(self):
        self.cancelled = True

    def apply(self, wake, executor, routine):
        def inner_wake(value):
            if not self.cancelled:
                wake(self.readfile.read(self.length))
        IOSuspend.apply(self, inner_wake, executor, routine)

class WriteFile(IOSuspend):
    def __init__(self, writefile, data, selectfile=None):
        if selectfile is None: selectfile = writefile
        IOSuspend.__init__(self, selectfile, 'w')
        self.writefile = writefile
        self.data = data
        self.cancelled = False

    def cancel(self):
        self.cancelled = True

    def apply(self, wake, executor, routine):
        def inner_wake(value):
            if not self.cancelled:
                wake(self.writefile.write(self.data))
        IOSuspend.apply(self, inner_wake, executor, routine)

class Executor:
    def __init__(self):
        self.routines = {}
        self.daemons = set()
        self.suspended = set()
        self.listening = {}
        self.sleeps = []
        self.selectfiles = ([], [], [])

    def add(self, routine, value=None, daemon=False):
        self.routines[routine] = value
        if daemon: self.daemons.add(routine)

    def remove(self, routine):
        self.suspended.discard(routine)
        self.daemons.discard(routine)
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
        def clear(lst, rem): lst[:] = [f for f in lst if f not in rem]
        if readable: clear(self.selectfiles[0], frozenset(readable))
        if writable: clear(self.selectfiles[1], frozenset(writable))
        if exceptable: clear(self.selectfiles[2], frozenset(exceptable))
        for f in readable: self.trigger(f, 'r')
        for f in writable: self.trigger(f, 'w')
        for f in exceptable: self.trigger(f, 'x')

    def close(self):
        for r in self.routines:
            r.close()
        for r in self.suspended:
            r.close()
        self.routines = {}
        self.suspended = set()
        self.listening = {}
        self.sleeps = []
        self.selectfiles = ([], [], [])

    def __call__(self):
        def make_wake(routine):
            return lambda value: self._wake(routine, value)
        while self.routines or self.suspended:
            runqueue = tuple(self.routines.items())
            if all(e[0] in self.daemons for e in runqueue):
                break
            for r, v in runqueue:
                self.routines[r] = None
                try:
                    suspend = r.send(v)
                except StopIteration:
                    self._done(r)
                    continue
                if suspend is None:
                    continue
                self._suspend(r)
                suspend.apply(make_wake(r), self, r)
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

def sigpipe_handler(rfp, wfp):
    try:
        while 1:
            yield ReadFile(rfp, 1)
            wakelist = []
            while 1:
                pid, status = os.waitpid(-1, os.WNOHANG)
                if pid == 0: break
                code = status >> 8 if status & 0xFF00 else -status
                wakelist.append((ProcessTag(pid), code))
            yield Trigger(*wakelist)
    finally:
        for fp in (rfp, wfp):
            try:
                fp.close()
            except IOError:
                pass

def set_sigpipe(executor, coroutine=sigpipe_handler):
    rfd, wfd = os.pipe()
    rfp, wfp = os.fdopen(rfd, 'rb', 0), os.fdopen(wfd, 'wb', 0)
    try:
        signal.set_wakeup_fd(wfd)
        inst = coroutine(rfp, wfp)
        executor.add(inst, daemon=True)
    except Exception:
        for fd in (rfd, wfd):
            try:
                os.close(fd)
            except IOError:
                pass
        raise
    return inst

def run(routines):
    ex = Executor()
    for r in routines: ex.add(r)
    ex()
