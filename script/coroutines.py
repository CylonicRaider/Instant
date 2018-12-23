
# -*- coding: ascii -*-

# A PEP 342 generator-based coroutine framework.

import sys, os, time
import heapq
import traceback
import errno, signal, socket, select
import subprocess

SIGPIPE_CHUNK_SIZE = 1024
LINEREADER_CHUNK_SIZE = 16384

class Tag(object):
    """
    Tag(value) -> new instance

    A strongly typed comparable wrapper around an object

    A Tag compares equal to another object iff the other object is of the same
    type as the Tag and has an equal value; for example, this can be used to
    differentiate file descriptors from process ID-s.

    The value passed to the constructor is available as the "value" instance
    attribute.
    """

    def __init__(self, value):
        "Constructor; see class docstring for details"
        self.value = value

    def __hash__(self):
        "Compute a hash code of this instance"
        return hash((self.__class__, self.value))

    def __eq__(self, other):
        "Test whether this instance is equal to other"
        return other.__class__ == self.__class__ and other.value == self.value

    def __ne__(self, other):
        "Test whether this instance is not equal to other"
        return other.__class__ != self.__class__ or other.value != self.value

class ProcessTag(Tag):
    "A Tag subclass for labeling process ID-s"

class Suspend(object):
    """
    A Suspend encapsulates a temporary interruption of a coroutine's execution
    in order to perform some spcecial action

    "Special actions" include blocking I/O (which is multiplexed through a
    central select() loop), sleeping (which would make other coroutines
    unresponsive otherwise), and inter-coroutine communication. Suspends can
    be composed in various ways enabling concurrent execution of multiple
    actions; see CombinationSuspend subclasses for that. A Suspend may only
    be used (i.e. yielded from a coroutine) only once (unless otherwise
    noted); attempting to use it multiple times will result in unpredictable
    behavior.

    The Suspend class is abstract; subclasses should override the apply()
    method to specialize what they actually do.

    Some Suspend subclasses may allow "cancelling" their instances; this
    should abort any pending actions corresponding to the suspend. Cancelling
    enables suspends like Any to "race" multiple suspends against each other
    and to only commit the first suspend to finish.
    """

    def apply(self, wake, executor, routine):
        """
        Initiate the action corresponding to this suspend

        wake is a callback to be invoked when the action is done; it must be
        used to report results back to the caller. executor is the Executor
        instance this suspend is invoked in; its facilities (like listen() --
        trigger()) may be used. routine is the coroutine that this suspend
        originates from; only a few specialized suspends use this. The return
        value is ignored.

        The wake callback accepts a single positional argument that is a
        2-tuple (code, value) or the None singleton (which is equivalent to
        (0, None), but must be handled by consumers of the argument). The code
        field represents the "return channel" employed; it must be 0 for a
        normal returned value or 1 for a raised exception (note that an
        exception object may be returned regularly or raised, so that this
        distinction is necessary). The value field is the actual value being
        passed to the callback. The return value of the callback is ignored.
        When a composing suspend encounters an exception, it should propagate
        it to its callback and cancel any pending nested suspends.

        This implementation of the method is abstract; invoking it raises a
        NotImplementedError.

        The action may finish asynchronously or immediately (invoking wake
        before apply() returns). When composing suspends, the wake callback
        may be wrapped in another function to provide specialized behavior,
        while executor and routine should be passed on unmodified (unless
        there is a particular reason not to do so).
        """
        raise NotImplementedError

    def cancel(self):
        """
        Abort any pending actions associated with this suspend

        After cancel() is called, a suspend should never invoke the wake
        callback passed to it in its apply() method. This method may be
        invoked after apply() or without a preceding call to apply();
        composing suspends should not apply() suspends after having cancelled
        them.

        The default implementation raises a TypeError; cancellable suspends
        must implement this method themselves (or inherit it from, e.g.,
        SimpleCancellable).
        """
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
        self._finished_count = 0
        self._raised = False

    def _finish(self, index, value, callback):
        if self._finished[index]:
            raise RuntimeError('Attempting to wake a coroutine more than '
                'once')
        elif self._finished_count == -1:
            return
        if value is None:
            value = (0, None)
        if value[0] == 1:
            self._finished_count = -1
            self._raised = True
            callback(value)
            return
        self.result[index] = value[1]
        self._finished[index] = True
        self._finished_count += 1
        if self._finished_count == len(self.children):
            self._finished_count = -1
            callback((0, self.result))

    def apply(self, wake, executor, routine):
        def make_wake(index):
            return lambda value: self._finish(index, value, wake)
        for n, s in enumerate(self.children):
            if self._raised: break
            s.apply(make_wake(n), executor, routine)
        if self._raised:
            self.cancel()
        elif len(self.children) == self._finished_count == 0:
            self._finished_count = -1
            wake((0, self.result))

    def cancel(self):
        self._finished_count = -1
        CombinationSuspend.cancel(self)

class Any(CombinationSuspend):
    def __init__(self, *suspends):
        CombinationSuspend.__init__(self, suspends)
        self._do_wake = True

    def _wake(self, suspend, value, callback):
        if not self._do_wake: return
        self._do_wake = False
        if value is None:
            value = (0, None)
        if value[0] == 1:
            callback(value)
        else:
            callback((0, (suspend, value[1])))
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
        CombinationSuspend.__init__(self, list(suspends))
        self._pending = set(suspends)
        self._waiting = set()
        self._finished = {}
        self._callback = None

    def is_empty(self):
        return not (self._pending or self._waiting or self._finished)

    def add(self, suspend):
        self.children.append(suspend)
        self._pending.add(suspend)

    def _wake(self, suspend, value):
        try:
            self._waiting.remove(suspend)
        except KeyError:
            raise RuntimeError('Trying to wake non-suspended coroutine')
        if self._callback is None:
            if suspend in self._pending:
                raise RuntimeError('Trying to wake coroutine more than once')
            self._pending[suspend] = value
        else:
            self._finish(suspend, value)

    def _finish(self, suspend, value):
        if value is None:
            value = (0, None)
        if value[0] == 1:
            self._callback(value)
        else:
            self._callback((0, item))
        self._callback = None
        self._finished.pop(item[0], None)
        try:
            self.children.remove(item[0])
        except ValueError:
            pass

    def apply(self, wake, executor, routine):
        def make_wake(suspend):
            return lambda value: self._wake(suspend, value)
        self._callback = wake
        if self._pending:
            pending = tuple(self._pending)
            self._pending.clear()
            self._waiting.update(pending)
            for p in pending:
                p.apply(make_wake(p), executor, routine)
        if self._finished:
            self._finish(*self._finished.popitem())
        elif self.is_empty() and self._callback:
            wake((None, None))
            self._callback = None

    def cancel(self):
        self._pending.clear()
        self._waiting.clear()
        self._finished.clear()
        self._callback = None
        CombinationSuspend.cancel(self)
        self.children[:] = []

class WrapperSuspend(CombinationSuspend):
    def __init__(self, wrapped, process=None):
        CombinationSuspend.__init__(self, (wrapped,))
        self.process = process

    def apply(self, wake, executor, routine):
        def inner_wake(value):
            if value is None:
                value = (0, None)
            elif value[0] == 1:
                wake(value)
                return
            try:
                if self.process is not None:
                    value = (0, self.process(value[1]))
            except Exception as exc:
                value = (1, exc)
            wake(value)
        self.children[0].apply(inner_wake, executor, routine)

class ControlSuspend(Suspend): pass

class Trigger(ControlSuspend):
    def __init__(self, *triggers):
        self.triggers = triggers

    def apply(self, wake, executor, routine):
        for trig in self.triggers:
            if isinstance(trig, (tuple, list)):
                tag, value = trig
            else:
                tag, value = trig, None
            executor.trigger(tag, (0, value))
        wake((0, None))

class Exit(ControlSuspend):
    def __init__(self, result=None):
        self.result = result

    def apply(self, wake, executor, routine):
        routine.close()
        executor._done(routine, (0, self.result))

class Spawn(ControlSuspend):
    def __init__(self, target, daemon=False):
        self.target = target
        self.daemon = daemon

    def apply(self, wake, executor, routine):
        executor.add(self.target, daemon=self.daemon)
        wake(None)

class SimpleCancellable(Suspend):
    def __init__(self):
        self.cancelled = False
        self._cancel_cb = None

    def cancel(self):
        self.cancelled = True
        cb, self._cancel_cb = self._cancel_cb, None
        if cb: cb()

    def listen(self, executor, target, callback):
        executor.listen(target, callback)
        self._cancel_cb = lambda: executor.stop_listening(target, callback)

    def listen_reapply(self, wake, executor, routine, target):
        self.listen(executor, target,
                    lambda v: self.apply(wake, executor, routine))

class Call(ControlSuspend, SimpleCancellable):
    def __init__(self, target, daemon=False):
        self.target = target
        self.daemon = daemon

    def apply(self, wake, executor, routine):
        executor.add(self.target, daemon=self.daemon)
        self.listen(executor, self.target, wake)

class Listen(ControlSuspend, SimpleCancellable):
    def __init__(self, target):
        SimpleCancellable.__init__(self)
        self.target = target

    def apply(self, wake, executor, routine):
        self.listen(executor, self.target, wake)

class Join(Listen): pass

class Sleep(SimpleCancellable):
    def __init__(self, waketime, absolute=False):
        if not absolute: waketime += time.time()
        SimpleCancellable.__init__(self)
        self.waketime = waketime

    def __hash__(self):
        return hash((self.waketime, id(self)))

    def __lt__(self, other):
        return (self.waketime, id(self)) <  (other.waketime, id(other))
    def __le__(self, other):
        return (self.waketime, id(self)) <= (other.waketime, id(other))
    def __eq__(self, other):
        return (self.waketime, id(self)) == (other.waketime, id(other))
    def __ne__(self, other):
        return (self.waketime, id(self)) != (other.waketime, id(other))
    def __ge__(self, other):
        return (self.waketime, id(self)) >= (other.waketime, id(other))
    def __gt__(self, other):
        return (self.waketime, id(self)) >  (other.waketime, id(other))

    def apply(self, wake, executor, routine):
        executor.add_sleep(self)
        self.listen(executor, self, wake)

class IOSuspend(SimpleCancellable):
    SELECT_MODES = {'r': 0, 'w': 1, 'x': 2}

    def __init__(self, selectfile, mode):
        if mode not in self.SELECT_MODES:
            raise ValueError('Invalid I/O mode: %r' % (mode,))
        SimpleCancellable.__init__(self)
        self.selectfile = selectfile
        self.mode = mode

    def _do_io(self, mode):
        return None

    def apply(self, wake, executor, routine):
        def inner_wake(value):
            if value is not None and value[0] == 1:
                wake(value)
            else:
                result = self._do_io(value[1])
                wake((0, result))
        executor.add_select(self.selectfile, self.SELECT_MODES[self.mode])
        self.listen(executor, self.selectfile, inner_wake)

class ReadFile(IOSuspend):
    def __init__(self, readfile, length, selectfile=None):
        if selectfile is None: selectfile = readfile
        IOSuspend.__init__(self, selectfile, 'r')
        self.readfile = readfile
        self.length = length

    def _do_io(self, mode):
        try:
            return self.readfile.read(self.length)
        except AttributeError:
            try:
                return self.readfile.recv(self.length)
            except AttributeError:
                return os.read(self.readfile, self.length)

class WriteFile(IOSuspend):
    def __init__(self, writefile, data, selectfile=None):
        if selectfile is None: selectfile = writefile
        IOSuspend.__init__(self, selectfile, 'w')
        self.writefile = writefile
        self.data = data

    def _do_io(self, mode):
        try:
            return self.writefile.write(self.data)
        except AttributeError:
            try:
                return self.writefile.send(self.data)
            except AttributeError:
                return os.write(self.writefile, self.data)

class AcceptSocket(IOSuspend):
    def __init__(self, sock, selectfile=None):
        if selectfile is None: selectfile = sock
        IOSuspend.__init__(self, selectfile, 'r')
        self.sock = sock

    def _do_io(self, mode):
        return self.sock.accept()

class UtilitySuspend(Suspend): pass

class ReadAll(UtilitySuspend):
    def __init__(self, readfile, length, selectfile=None):
        self.readfile = readfile
        self.length = length
        self.selectfile = selectfile
        self.read = None

    def apply(self, wake, executor, routine):
        def inner_wake(result):
            if result is not None and result[0] == 1:
                wake(result)
                return
            elif result is None:
                result = (0, result)
            if self.read is None:
                self.read = result[1]
            else:
                self.read += result[1]
            if result[1]:
                self.apply(wake, executor, routine)
            else:
                wake((0, self.read))
        length = 0 if self.read is None else len(self.read)
        if length == self.length:
            wake((0, self.read))
            return
        delegate = ReadFile(self.readfile, self.length - length,
                            self.selectfile)
        delegate.apply(inner_wake, executor, routine)

class WriteAll(UtilitySuspend):
    def __init__(self, writefile, data, selectfile=None):
        self.writefile = writefile
        self.data = data
        self.selectfile = selectfile
        self.written = 0

    def apply(self, wake, executor, routine):
        def inner_wake(result):
            if result is not None and result[0] == 1:
                wake(result)
                return
            elif result is None or result[1] is None:
                self.written = len(self.data)
            else:
                self.written += result[1]
            self.apply(wake, executor, routine)
        if self.written == len(self.data):
            wake((0, self.written))
            return
        delegate = WriteFile(self.writefile, self.data[self.written:],
                             self.selectfile)
        delegate.apply(inner_wake, executor, routine)

class SpawnProcess(UtilitySuspend):
    def __init__(self, **params):
        self.params = params

    def apply(self, wake, executor, routine):
        proc = subprocess.Popen(**self.params)
        if hasattr(executor, 'waits'):
            executor.waits.add(proc)
        wake((0, proc))

class WaitProcess(UtilitySuspend, SimpleCancellable):
    def __init__(self, target):
        SimpleCancellable.__init__(self)
        self.target = target

    def apply(self, wake, executor, routine):
        if not hasattr(executor, 'waits'):
            raise RuntimeError('Executor not equipped for waiting for '
                'processes')
        if hasattr(self.target, 'poll'):
            res = self.target.poll()
            if res is not None:
                wake((0, res))
                return
            executor.waits.add(self.target)
            eff_target = ProcessTag(self.target.pid)
        elif isinstance(self.target, int):
            eff_target = ProcessTag(self.target)
        else:
            eff_target = self.target
        self.listen(executor, eff_target, wake)

class Executor:
    def __init__(self):
        self.routines = {}
        self.daemons = set()
        self.suspended = set()
        self.listening = {}
        self.selectfiles = (set(), set(), set())
        self.sleeps = []
        self.error_cb = None

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

    def _done(self, routine, result):
        self.remove(routine)
        return self.trigger(routine, result)

    def listen(self, event, callback):
        try:
            self.listening[event].add(callback)
        except KeyError:
            self.listening[event] = set((callback,))

    def stop_listening(self, event, callback):
        if event not in self.listening: return
        self.listening[event].discard(callback)

    def trigger(self, event, result=None):
        callbacks = self.listening.pop(event, ())
        for cb in callbacks:
            self._run_callback(cb, (result,), final=True)
        return len(callbacks)

    def add_select(self, file, index):
        self.selectfiles[index].add(file)

    def remove_selects(self, *files):
        self.selectfiles[0].difference_update(files)
        self.selectfiles[1].difference_update(files)
        self.selectfiles[2].difference_update(files)
        result = (1, EOFError('File closed'))
        for f in files: self.trigger(f, result)

    def _done_select(self, readable, writable, exceptable):
        self.selectfiles[0].difference_update(readable)
        self.selectfiles[1].difference_update(writable)
        self.selectfiles[2].difference_update(exceptable)
        for f in readable: self.trigger(f, (0, 'r'))
        for f in writable: self.trigger(f, (0, 'w'))
        for f in exceptable: self.trigger(f, (0, 'x'))

    def add_sleep(self, sleep):
        heapq.heappush(self.sleeps, sleep)

    def _finish_sleeps(self, now=None):
        if now is None: now = time.time()
        while self.sleeps and self.sleeps[0].waketime <= now:
            sleep = heapq.heappop(self.sleeps)
            self.trigger(sleep)

    def on_error(self, exc, source):
        if self.error_cb and self.error_cb(exc, source): return
        lines = ['Uncaught exception in %r:\n' % (source,)]
        lines.extend(traceback.format_exception(*sys.exc_info()))
        sys.stderr.write(''.join(lines))

    def _run_callback(self, func, args, kwds=None, final=False):
        if kwds is None: kwds = {}
        try:
            return (0, func(*args, **kwds))
        except Exception as exc:
            if final: self.on_error(exc, func)
            return (1, exc)

    def close(self):
        for r in tuple(self.routines):
            r.close()
        for r in tuple(self.suspended):
            r.close()
        self.routines = {}
        self.suspended = set()
        self.listening = {}
        self.sleeps = []
        self.selectfiles = (set(), set(), set())

    def run(self):
        def all_daemons():
            return (self.daemons.issuperset(self.routines) and
                    self.daemons.issuperset(self.suspended))
        def abort_routine(r, exc):
            if not self._done(r, (1, exc)):
                self.on_error(exc, r)
        def make_wake(routine):
            return lambda value: self._wake(routine, value)
        while self.routines or self.suspended:
            if all_daemons():
                break
            runqueue = tuple(self.routines.items())
            for r, v in runqueue:
                self.routines[r] = None
                if v is None: v = (0, None)
                # Here, we cannot use _run_callback() since we need to know
                # after the fact whether to run on_error() or not; as a bonus,
                # we get a more precise error source.
                try:
                    resume = (r.send, r.throw)[v[0]]
                    suspend = resume(v[1])
                except StopIteration:
                    self._done(r, None)
                    continue
                except Exception as exc:
                    abort_routine(r, exc)
                    continue
                if suspend is None:
                    continue
                elif isinstance(suspend, BaseException):
                    abort_routine(r, suspend)
                    continue
                self._suspend(r)
                res = self._run_callback(suspend.apply, (make_wake(r), self,
                                                         r))
                if res[0] == 1:
                    self._wake(r, res)
            if all_daemons():
                break
            if any(self.selectfiles):
                if self.routines:
                    timeout = 0
                elif self.sleeps:
                    timeout = self.sleeps[0].waketime - time.time()
                else:
                    timeout = None
                sf = self.selectfiles
                try:
                    result = select.select(sf[0], sf[1], sf[2], timeout)
                except select.error as e:
                    if hasattr(e, 'errno'): # Py3K
                        if e.errno != errno.EINTR: raise
                    else: # Py2K
                        if e.args[0] != errno.EINTR: raise
                else:
                    self._done_select(*result)
            elif self.sleeps and not self.routines:
                time.sleep(self.sleeps[0].waketime - time.time())
            self._finish_sleeps()

    def __call__(self):
        self.run()

class Lock(object):
    class _Acquire(SimpleCancellable):
        def __init__(self, parent):
            SimpleCancellable.__init__(self)
            self.parent = parent

        def apply(self, wake, executor, routine):
            if self.parent.locked:
                self.listen_reapply(wake, executor, routine, self.parent)
            else:
                self.parent.locked = True
                self.parent._trigger = lambda: executor.trigger(self.parent)
                wake((0, None))

    def __init__(self):
        self.locked = False
        self._trigger = None

    def Acquire(self):
        return self._Acquire(self)

    def release(self):
        if not self.locked:
            raise RuntimeError('Releasing unlocked lock')
        self.locked = False
        callback, self._trigger = self._trigger, None
        callback()

class StateSwitcher(object):
    class _Wait(SimpleCancellable):
        def __init__(self, parent, match):
            SimpleCancellable.__init__(self)
            self.parent = parent
            self.match = match

        def apply(self, wake, executor, routine):
            if self.parent.state == self.match:
                wake((0, None))
            else:
                self.listen_reapply(wake, executor, routine, self.parent)

    class _Toggle(SimpleCancellable):
        def __init__(self, parent, match, new, wait):
            SimpleCancellable.__init__(self)
            self.parent = parent
            self.match = match
            self.new = new
            self.wait = wait

        def apply(self, wake, executor, routine):
            if self.parent.state == self.match:
                self.parent.state = self.new
                executor.trigger(self.parent, (0, self.new))
                wake((0, True))
            elif not self.wait:
                wake((0, False))
            else:
                self.listen_reapply(wake, executor, routine, self.parent)

    class _Set(Suspend):
        def __init__(self, parent, new):
            self.parent = parent
            self.new = new

        def apply(self, wake, executor, routine):
            prev_state = self.parent.state
            self.parent.state = self.new
            executor.trigger(self.parent, (0, self.new))
            wake((0, prev_state))

    def __init__(self, state=None):
        self.state = state

    def Wait(self, match):
        return self._Wait(self, match)

    def Toggle(self, match, new, wait=False):
        return self._Toggle(self, match, new, wait)

    def Set(self, new):
        return self._Set(self, new)

class BinaryLineReader(object):
    class _ReadLine(Suspend):
        def __init__(self, parent):
            self.parent = parent
            self._delegate = None

        def apply(self, wake, executor, routine):
            def inner_wake(result):
                if result[0] == 1:
                    wake(result)
                    return
                elif result[1]:
                    self.parent._buffer += result[1]
                else:
                    self.parent._eof = True
                self._delegate = None
                self.apply(wake, executor, routine)
            line = self.parent._extract_line()
            if line is not None:
                wake((0, line))
                return
            self._delegate = ReadFile(self.parent.file,
                                      LINEREADER_CHUNK_SIZE)
            self._delegate.apply(inner_wake, executor, routine)

        def cancel(self):
            if self._delegate is not None:
                self._delegate.cancel()

    def __init__(self, file, encoding=None, errors=None):
        self.file = file
        self.encoding = encoding
        self.errors = errors
        self.chunk_size = LINEREADER_CHUNK_SIZE
        self._buffer = b''
        self._eof = False

    def _extract_line(self):
        line, sep, rest = self._buffer.partition(b'\n')
        if not sep and not self._eof:
            return None
        self._buffer = rest
        line += sep
        if self.encoding is not None:
            line = line.decode(self.encoding, self.errors)
        return line

    def ReadLine(self):
        return self._ReadLine(self)

def sigpipe_handler(rfd, waits, cleanup):
    try:
        while 1:
            yield ReadFile(rfd, SIGPIPE_CHUNK_SIZE)
            wakelist = []
            for w in tuple(waits):
                res = w.poll()
                if res is not None:
                    waits.remove(w)
                    wakelist.append((ProcessTag(w.pid), res))
            while 1:
                try:
                    pid, status = os.waitpid(-1, os.WNOHANG)
                except OSError as e:
                    if e.errno != errno.ECHILD: raise
                    break
                if pid == 0: break
                code = -(status & 0x7F) if status & 0xFF else status >> 8
                wakelist.append((ProcessTag(pid), code))
            yield Trigger(*wakelist)
    finally:
        cleanup()

def set_sigpipe(executor, coroutine=sigpipe_handler):
    def cleanup(reset_signals=True):
        if reset_signals:
            signal.signal(signal.SIGCHLD, old_handler)
            signal.set_wakeup_fd(-1)
        for fd in (rfd, wfd):
            try:
                os.close(fd)
            except IOError:
                pass
        try:
            del executor.waits
        except AttributeError:
            pass
    rfd, wfd = os.pipe()
    waits = set()
    try:
        signal.set_wakeup_fd(wfd)
        old_handler = signal.signal(signal.SIGCHLD, lambda sn, f: None)
        inst = coroutine(rfd, waits, cleanup)
        executor.add(inst, daemon=True)
        executor.waits = waits
    except Exception:
        cleanup(False)
        raise
    return inst

def const(value=None):
    yield coroutines.Exit(value)

def constRaise(exc, excclass=RuntimeError):
    if not isinstance(exc, BaseException):
        raise excclass(exc)
    yield exc

def run(routines=(), main=None, sigpipe=False, on_error=None):
    def main_routine():
        result[0] = yield main
    ex = Executor()
    for r in routines: ex.add(r)
    if main: ex.add(main_routine())
    if sigpipe: set_sigpipe(ex)
    if on_error is not None: ex.error_cb = on_error
    result = [None]
    try:
        ex.run()
    finally:
        ex.close()
    return result[0]
