
# -*- coding: ascii -*-

"""
A PEP 342 generator-based coroutine framework

This module implements the cooperative multitasking in Python using generator
functions. Multiple coroutines can run concurrently, exchanging control with
each other by suspending themselves regularly, all staying within the same
thread of execution. Coroutines perform potentially blocking operations (such
as I/O or sleeping) by suspending themselves and letting the executor together
with special "suspend" objects manage the actual operations.

A listing of the important classes and functions follows; for a more lengthy
introduction to the library, read on.
Executor        : Schedules coroutines and provides various services to them;
                  one main entry point into the module.
Suspend         : The base class of all suspend objects; see its subclasses
                  for functionality available to coroutines.
Lock            : Provides mutex-like functionality (across multiple suspends)
                  to coroutines.
StateSwitcher   : Provides management of a state variable to synchronize
                  multiple coroutines.
BinaryLineReader: Convenience class implementing reading lines from a file (or
                  socket) based on more "primitive" coroutine suspends.
set_sigpipe     : Configures an Executor to be able to wait() for child
                  processes (this requires changing global state and is thus
                  a module-level function).
const           : Trivial coroutine always exiting with a given value.
const_raise     : Trivial coroutine always raising an exception.
run             : Convenience function for constructing, configuring, running,
                  and cleaning up an Executor.

As remarked above, coroutines are implemented as generator functions, like the
following example:

    def ticker(delay, output):
        while 1:
            print ('Tick!')
            yield Sleep(delay)
            print ('Tock!')
            yield Sleep(delay)

This function regularly writes strings to standard output (remark that the
example uses the blocking print since it is assumed to be fast enough) and
sleeps in between them by using a suspend object. Suspend objects can perform
various things; aside from the already-mentioned I/O and sleeping, suspends
can also perform inter-coroutine communication (with less overhead than
regular polling of shared variables) or compose other suspends. For example,
the following suspend reads from a file with a timeout:

    suspend, result = yield Any(ReadFile(f, 1024), Sleep(5))

The Any suspend returns the first suspend to finish along with its result: if
the ReadFile does not finish within five seconds, the Sleep's result is
reported instead. The other sub-suspend of the Any is *cancelled* (which is an
operation not supported by all suspends), so that no data are actually read
should the sleep finish first. As seen in the example above, suspends can
return values to the invoking coroutine (or to enclosing suspends); composing
suspends can manipulate the return values of their nested suspends.

In order to become effective, a suspend object must be yielded (recall that
coroutines are implemented as generators) from a coroutine to its executor,
which schedules the coroutines and provides services to suspend objects, such
as a central select loop (which is used by ReadFile etc.). Once yielded, it is
up to the suspend to wake up the coroutine whenever the suspend is done (which
might be immediately or, for the Exit suspend, never).

A coroutine can also use a bare "yield" to suspend itself without performing
any special action; it will be immediately resumed after other coroutines have
had a chance to run (which happens every time a coroutine yields).

The use of generator functions as coroutines forces some restrictions upon
language features; in particular, generator functions cannot both be
coroutines and be used for their original purpose of driving a for loop, and
coroutines cannot directly call other coroutines. No means of working around
the for loop restriction are provided by the framework; subroutines can be
implemented using the Call suspend. Additionally, coroutines cannot directly
return values (since returning a non-None value is not permitted for a
generator); the Exit suspend provides that functionality instead.
"""

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
        "Initializer; see class docstring for details"
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
    actions; see CombinationSuspend subclasses for that. A Suspend may be used
    (i.e. yielded from a coroutine) only once (unless otherwise noted);
    attempting to use it multiple times will result in unpredictable behavior.

    Some Suspend subclasses may allow "cancelling" their instances; this
    should abort any pending actions corresponding to the suspend. Cancelling
    enables suspends like Any to "race" multiple suspends against each other
    and to only commit the first suspend which finishes.

    The Suspend class is abstract; subclasses should override the apply()
    method to specialize what they actually do. Suspend objects must be
    hashable and usable as dictionary keys; while they may override comparison
    operators (like Sleep does), different Suspend objects (w.r.t. the
    identity comparison "is") must always be unequal.
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
    """
    CombinationSuspend(children) -> new instance

    A CombinationSuspend composes multiple nested suspends

    This class provides a standartized attribute for holding the "children"
    this suspend composes and an implementation of the cancel() method;
    apply() is kept abstract.
    """

    def __init__(self, children):
        "Initializer; see class docstring for details"
        self.children = children

    def cancel(self):
        """
        Cancel this suspend

        The default implementation cancels each child.
        """
        for c in self.children:
            c.cancel()

class All(CombinationSuspend):
    """
    All(*suspends) -> new instance

    Wait for all suspends to finish and return their results as a list

    When any of the nested suspends raises an exception, it is propagated to
    the caller and all other suspends are cancelled. When all nested suspends
    finish normally, a list consisting of their results (in the same order as
    the suspends) is returned. Cancelling an All suspend cancels all of its
    children.
    """

    # All is the suspend incarnation of product types -- the result is a tuple
    # (implemented as a Python list) of the results of the nested suspends.

    def __init__(self, *suspends):
        "Initializer; see class docstring for details"
        CombinationSuspend.__init__(self, suspends)
        self.result = [None] * len(suspends)
        self._finished = [False] * len(suspends)
        self._finished_count = 0
        self._raised = False

    def _finish(self, index, value, callback):
        "Internal callback invoked when a nested suspend finishes"
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
        "Apply this suspend; see class docstring for details"
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
        "Cancel this suspend"
        self._finished_count = -1
        CombinationSuspend.cancel(self)

class Any(CombinationSuspend):
    """
    Any(*suspends) -> new instance

    Wait for the first suspend to finish and return its result (along with it)

    When a suspend finishes normally, a 2-tuple consisting of the suspend and
    its result is returned to the caller. When a suspend finishes with an
    exception, it is propagated directly. In either case, all other suspends
    are cancelled. Cancelling an Any suspend cancels all of its children.
    """

    # Any is the suspend incarnation of coproduct types (tagged unions) -- the
    # result is, as noted, an index (the suspend) together with the
    # corresponding value.

    def __init__(self, *suspends):
        "Initializer; see class docstring for details"
        CombinationSuspend.__init__(self, suspends)
        self._do_wake = True

    def _wake(self, suspend, value, callback):
        "Internal callback invoked when a nested suspend finishes"
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
        "Apply this suspend; see the class docstring for details"
        def make_wake(suspend):
            return lambda value: self._wake(suspend, value, wake)
        for s in self.children:
            if not self._do_wake: break
            s.apply(make_wake(s), executor, routine)

    def cancel(self):
        "Cancel this suspend"
        self._do_wake = False
        CombinationSuspend.cancel(self)

class Selector(CombinationSuspend):
    """
    Selector(*suspends) -> new instance

    A Selector allows running multiple suspends in parallel and retrieving
    their results whenever they arrive

    When apply()ed -- which, as an exception, may happen multiple times --, a
    Selector will apply() all pending child suspends (i.e. suspends added via
    the constructor or add() that have not been apply()ed yet) and return the
    result of a finished child (or suspend the calling routine until there is
    one); if it has no children at all, the Selector will finish immediately,
    returning a (None, None) tuple. Since Selector never cancels its children
    (unless cancelled itself), one can use it to provide something similar to
    the Any suspend with children that cannot be cancelled.

    The result of each child suspend is reported as a 2-tuple (child, value),
    where child is the child suspend that finished and value is its result;
    if a child suspend resulted in an exception, it is re-raised instead.
    """

    def __init__(self, *suspends):
        "Initializer; see class docstring for details"
        CombinationSuspend.__init__(self, list(suspends))
        self._pending = set(suspends)
        self._waiting = set()
        self._finished = {}
        self._callback = None

    def is_empty(self):
        """
        Test whether this Selector is empty

        A Selector is empty if it has no children pending to be apply()ed, no
        children currently running, and no finished children pending retrieval
        of their results.
        """
        return not (self._pending or self._waiting or self._finished)

    def add(self, suspend):
        """
        Add a new child suspend to this Selector

        A child is initially in a "pending" state, until it is apply()ed on
        the next apply() of the Selector. A child is automatically removed
        when its result is retrieved via apply().
        """
        self.children.append(suspend)
        self._pending.add(suspend)

    def _wake(self, suspend, value):
        "Internal callback invoked whenever a nested suspend finishes"
        try:
            self._waiting.remove(suspend)
        except KeyError:
            raise RuntimeError('Trying to wake non-suspended coroutine')
        if self._callback is None:
            if suspend in self._pending:
                raise RuntimeError('Trying to wake coroutine more than once')
            self._pending[suspend] = value
        else:
            self._report(suspend, value)

    def _report(self, suspend, value):
        "Internal; reports a child's result to the Selector's caller"
        if value is None:
            value = (0, None)
        if value[0] == 1:
            self._callback(value)
        else:
            self._callback((0, (suspend, value[1])))
        self._callback = None
        self._finished.pop(suspend, None)
        try:
            self.children.remove(suspend)
        except ValueError:
            pass

    def apply(self, wake, executor, routine):
        "Apply this suspend; see the class docstring for details"
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
            self._report(*self._finished.popitem())
        elif self.is_empty() and self._callback:
            wake((None, None))
            self._callback = None

    def cancel(self):
        """
        Cancel this suspend

        This cancels all children and resets all internal state (including the
        child list).
        """
        self._pending.clear()
        self._waiting.clear()
        self._finished.clear()
        self._callback = None
        CombinationSuspend.cancel(self)
        self.children[:] = []

class WrapperSuspend(CombinationSuspend):
    """
    WrapperSuspend(wrapped, process=None) -> new instance

    Perform a suspend and pass its result through a function

    When applied, this suspend first applies the wrapped suspend; if that
    results in an exception, it is passed through unmodified; otherwise,
    process is invoked with the result value as the only positional argument
    and the result of that is the final result of this suspend. A process of
    None is equivalent to passing through the value unmodified. Exceptions
    (deriving from the Exception class) produced when process is invoked are
    captured and propagated to the caller instead of process' return value.
    """

    def __init__(self, wrapped, process=None):
        "Initializer; see the class docstring for details"
        CombinationSuspend.__init__(self, (wrapped,))
        self.process = process

    def apply(self, wake, executor, routine):
        "Apply this suspend; see the class docstring for details"
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

class ControlSuspend(Suspend):
    """
    This is an umbrella class used as a base of suspends that roughly
    correspond to control flow constructs
    """

class Trigger(ControlSuspend):
    """
    Trigger(*triggers) -> new instance

    Invoke listeners for some objects with optional associated values

    When applied, each of the given triggers is split into a "tag" and an
    "associated value": If the trigger is a tuple, it must have two entries,
    the first being the tag and the other the associated value; otherwise, the
    tag is the trigger object and the associated value is None. For each of
    those pairs, the tag object is "triggered", notifying all listeners
    registered with the executor for it.

    Together with Listen, this allows a simple form of inter-coroutine
    communication by passing associated values to other coroutines listening
    on some predefined tag.
    """

    def __init__(self, *triggers):
        "Initializer; see class docstring for details"
        self.triggers = triggers

    def apply(self, wake, executor, routine):
        "Apply this suspend; see class docstring for details"
        for trig in self.triggers:
            if isinstance(trig, tuple):
                tag, value = trig
            else:
                tag, value = trig, None
            executor.trigger(tag, (0, value))
        wake((0, None))

class Exit(ControlSuspend):
    """
    Exit(result=None) -> new instance

    Finish this coroutine and return the given result

    The calling routine is close()d (raising a GeneratorExit exception), and
    the given result is passed to all listeners registered for the coroutine
    object (as if Trigger had been used on it).

    Together with Call, this can be used to implement subroutine calls using
    coroutines.
    """

    def __init__(self, result=None):
        "Initializer; see class docstring for details"
        self.result = result

    def apply(self, wake, executor, routine):
        "Apply this suspend; see class docstring for details"
        routine.close()
        executor._done(routine, (0, self.result))

class Spawn(ControlSuspend):
    """
    Spawn(target, daemon=False) -> new instance

    Add target to the list of running coroutines and finish immediately

    daemon specifies whether the coroutine is daemonic or not; when only
    daemonic coroutines are left, the executor stops running.

    The return value of target cannot be reliably retrieved since it could
    exit immediately. A single object cannot be used as a coroutine more than
    once.
    """

    def __init__(self, target, daemon=False):
        "Initializer; see class docstring for details"
        self.target = target
        self.daemon = daemon

    def apply(self, wake, executor, routine):
        "Apply this suspend; see class docstring for details"
        executor.add(self.target, daemon=self.daemon)
        wake(None)

class SimpleCancellable(Suspend):
    """
    An abstract class for cancellable suspends

    This class provides an implementation of the cancel() method that sets a
    flag on the instance and invokes (and clears) an optional callback.

    Convenience methods are provided for extending classes; these should not
    be invoked by external code.

    Instances of this class have a "cancelled" attribute, which indicates
    whether the suspend has been cancelled, and a "_cancel_cb" attribute,
    which is used by cancel() to undo any changes performed in apply().
    """

    def __init__(self):
        "Initializer; see class docstring for details"
        self.cancelled = False
        self._cancel_cb = None

    def cancel(self):
        """
        Cancel this suspend

        The "cancelled" instance attribute is set to True; the _cancel_cb
        instance attribute, if it is true, is invoked, and replaced with None
        (before being called) in any case.
        """
        self.cancelled = True
        cb, self._cancel_cb = self._cancel_cb, None
        if cb: cb()

    def listen(self, executor, target, callback):
        """
        Convenience function for cancellably listening on target

        Unless the suspend is cancelled in the meantime, callback is invoked
        when the executor triggers the given target.

        This invokes executor's listen() method on target and callback, and
        sets the _cancel_cb instance attribute to an anonymous function that
        invokes the executor's stop_listening() on them.
        """
        executor.listen(target, callback)
        self._cancel_cb = lambda: executor.stop_listening(target, callback)

    def listen_reapply(self, wake, executor, routine, target):
        """
        Convenience function for waiting for something

        A somewhat common pattern for a suspend is to listen on a certain
        object until some criterion is met; this method facilitates the
        pattern by listen()ing on the target and re-invoking the suspend's
        apply() method with the given arguments when the target is triggered.
        """
        self.listen(executor, target,
                    lambda v: self.apply(wake, executor, routine))

class Call(ControlSuspend, SimpleCancellable):
    """
    Call(target, daemon=False) -> new instance

    Spawn the given coroutine, wait for it to finish, and return its result

    daemon specifies whether the new coroutine should be daemonic; when only
    daemonic coroutines are left, the executor stops running.

    This is somewhat similar to a Spawn and a Join, but without race
    conditions. The same object may not be used as a coroutine more than once.
    """

    def __init__(self, target, daemon=False):
        "Initializer; see class docstring for details"
        self.target = target
        self.daemon = daemon

    def apply(self, wake, executor, routine):
        "Apply this suspend; see class docstring for details"
        executor.add(self.target, daemon=self.daemon)
        self.listen(executor, self.target, wake)

class Listen(ControlSuspend, SimpleCancellable):
    """
    Listen(target) -> new instance

    Wait until target is "triggered" and return the associated value

    This is the counterpart of the Trigger suspend. When the latter is
    invoked, all "listeners" registered for a "tag" (called target here) are
    invoked with some "associated value" which was passed to Trigger; Listen
    returns the associated value to its caller.

    Together with Trigger, this allows a simple form of inter-coroutine
    communication by passing associated values between listeners and
    triggers.
    """

    def __init__(self, target):
        "Initializer; see class docstring for details"
        SimpleCancellable.__init__(self)
        self.target = target

    def apply(self, wake, executor, routine):
        "Apply this suspend; see class docstring for details"
        self.listen(executor, self.target, wake)

class Join(Listen):
    """
    A thin wrapper around Listen

    Waiting for a coroutine to finish is exactly equivalent to Listen-ing on
    it; this class is provided for similarity with the join() method from
    Python's threading module.
    """

class Sleep(SimpleCancellable):
    """
    Sleep(waketime, absolute=False) -> new instance

    Suspends the calling coroutine either for or until the given waketime

    If absolute is true, waketime is interpreted as a UNIX timestamp (in
    seconds) after which to wake the routine; otherwise, it is interpreted
    relative to the current time. The "waketime" instance attribute always
    holds an absolute timestamp.
    """

    def __init__(self, waketime, absolute=False):
        "Initializer; see class docstring for details"
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
        "Apply this suspend; see class docstring for details"
        executor.add_sleep(self)
        self.listen(executor, self, wake)

class IOSuspend(SimpleCancellable):
    """
    IOSuspend(selectfile, mode) -> new instance

    A suspend that ties into the executor's select loop to perform I/O

    When applied, an IOSuspend waits for the given selectfile (a file
    descriptor, something with a fileno() method, or anything accepted by
    select.select()) to become ready for the given mode of I/O, which must
    be one of the strings 'r', 'w', or 'x' (for reading/writing/exceptional
    conditions, respectively), and invokes the do_io() method.

    The default implementation does not actually perform any I/O, but can be
    used to wait for a file descriptor's readiness.
    """

    SELECT_MODES = {'r': 0, 'w': 1, 'x': 2}

    def __init__(self, selectfile, mode):
        "Initializer; see class docstring for details"
        if mode not in self.SELECT_MODES:
            raise ValueError('Invalid I/O mode: %r' % (mode,))
        SimpleCancellable.__init__(self)
        self.selectfile = selectfile
        self.mode = mode

    def do_io(self, mode):
        """
        Perform I/O and return its result

        mode is the mode (one of 'r', 'w', or 'x') for which readiness was
        indicated by select(); since this class only registers for one
        specific mode of I/O, this will usually be equal to the same-named
        instance attribute.

        The code may throw an IOError, which is caught and propagated via the
        exception return channel; raising other exceptions will result in
        unpredictable behavior.
        """
        return None

    def apply(self, wake, executor, routine):
        "Apply this suspend; see class docstring for details"
        def inner_wake(value):
            if value is not None and value[0] == 1:
                wake(value)
                return
            try:
                result = self.do_io(value[1])
                wake((0, result))
            except IOError as exc:
                wake((1, exc))
        executor.add_select(self.selectfile, self.SELECT_MODES[self.mode])
        self.listen(executor, self.selectfile, inner_wake)

class ReadFile(IOSuspend):
    """
    ReadFile(readfile, length, selectfile=None) -> new instance

    Read up to length bytes from readfile

    readfile may be a file object (which must be unbuffered to avoid blocking
    the executor), a socket.socket object, or a file descriptor. selectfile is
    passed on to IOSuspend's initializer and defaults to readfile.
    """

    def __init__(self, readfile, length, selectfile=None):
        "Initializer; see class docstring for details"
        if selectfile is None: selectfile = readfile
        IOSuspend.__init__(self, selectfile, 'r')
        self.readfile = readfile
        self.length = length

    def do_io(self, mode):
        "Perform I/O; see class docstring for details"
        try:
            return self.readfile.read(self.length)
        except AttributeError:
            try:
                return self.readfile.recv(self.length)
            except AttributeError:
                return os.read(self.readfile, self.length)

class WriteFile(IOSuspend):
    """
    WriteFile(writefile, data, selectfile=None) -> new instance

    Write some leading bytes from data to writefile

    writefile may be a file object (which must be unbuffered to avoid blocking
    the executor), a socket.socket object, or a file descriptior. selectfile
    is passed on to IOSuspend's initializer and defaults to writefile.
    """

    def __init__(self, writefile, data, selectfile=None):
        "Initializer; see class docstring for details"
        if selectfile is None: selectfile = writefile
        IOSuspend.__init__(self, selectfile, 'w')
        self.writefile = writefile
        self.data = data

    def do_io(self, mode):
        "Perform I/O; see class docstring for details"
        try:
            return self.writefile.write(self.data)
        except AttributeError:
            try:
                return self.writefile.send(self.data)
            except AttributeError:
                return os.write(self.writefile, self.data)

class AcceptSocket(IOSuspend):
    """
    AcceptSocket(sock, selectfile=None) -> new instance

    Accept a connection on a listening socket

    sock should be a socket.socket object; selectfile defaults to it.
    """

    def __init__(self, sock, selectfile=None):
        "Initializer; see class docstring for details"
        if selectfile is None: selectfile = sock
        IOSuspend.__init__(self, selectfile, 'r')
        self.sock = sock

    def do_io(self, mode):
        "Perform I/O; see class docstring for details"
        return self.sock.accept()

class UtilitySuspend(Suspend):
    """
    An umbrella class covering miscellaneous suspends
    """

class ReadAll(UtilitySuspend):
    """
    ReadAll(readfile, length, selectfile=None) -> new instance

    Read from readfile until length bytes are read or EOF is reached

    Multiple partial reads may be performed and are transparently coalesced.
    The arguments are the same as for ReadFile.
    """

    def __init__(self, readfile, length, selectfile=None):
        "Initializer; see class docstring for details"
        self.readfile = readfile
        self.length = length
        self.selectfile = selectfile
        self.read = None

    def apply(self, wake, executor, routine):
        "Apply this suspend; see the class docstring for details"
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
    """
    WriteAll(writefile, data, selectfile=None) -> new instance

    Write all of data to writefile

    Multiple partial writes may be performed internally. The arguments are the
    same as for WriteFile.
    """

    def __init__(self, writefile, data, selectfile=None):
        "Initializer; see class docstring for details"
        self.writefile = writefile
        self.data = data
        self.selectfile = selectfile
        self.written = 0

    def apply(self, wake, executor, routine):
        "Apply this suspend; see the class docstring for details"
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
    """
    SpawnProcess(**params) -> new instance

    Create a new child process and register it with the coroutine executor

    params are passed on to subprocess.Popen(); the process object is returned
    to the caller. If the coroutine executor has been prepared for signal
    reception using set_sigpipe(), the process is registered to be wait()-ed
    upon whenever thec process hosting the executor receives a SIGCHLD.
    """

    def __init__(self, **params):
        "Initializer; see class docstring for details"
        self.params = params

    def apply(self, wake, executor, routine):
        "Apply this suspend; see the class docstring for details"
        proc = subprocess.Popen(**self.params)
        if hasattr(executor, 'waits'):
            executor.waits.add(proc)
        wake((0, proc))

class WaitProcess(UtilitySuspend, SimpleCancellable):
    """
    WaitProcess(target) -> new instance

    Wait for a child process and return its exit status

    If target has a "poll" attribute, it is assumed to be a subprocess.Popen
    instance, polled immediately, and registered for being waited upon.
    Otherwise, target is assumed to be an integral PID (or a ProcessTag
    instance) and is listened upon; the generic wait pool will trigger it
    whenever a process with that ID exits.

    Note that there is a race condition between the individual polls and the
    generic wait pool (a process might exit just after it had been polled
    individually). If the executor has not been prepared for signal reception
    using set_sigpipe(), applying this raises a RuntimeError.
    """

    def __init__(self, target):
        "Initializer; see class docstring for details"
        SimpleCancellable.__init__(self)
        self.target = target

    def apply(self, wake, executor, routine):
        "Apply this suspend; see the class docstring for details"
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
    """
    Executor() -> new instance

    Main class coordinating the execution of coroutines

    A coroutine Executor provides the following facilities:
    - Coroutine management: Coroutines (regular or "daemonic" ones) can be
      added and removed, and, most importantly, executed.
    - Event management: Arbitrary hashable objects can be "listened" upon,
      and the listeners can be "triggered", passing them some user-defined
      value.
    - Select loop and sleeping: Coroutines can wait for I/O to become
      available or sleep for some wall-clock time interval.
    - Error handling: A handler for uncaught exceptions in coroutines is
      provided.
    - Coroutine running: An Executor object's main loop can be started by
      calling the run() method or by calling the Executor object itself.
    """

    def __init__(self):
        "Initializer; see class docstring for details"
        self.routines = {}
        self.daemons = set()
        self.suspended = set()
        self.listening = {}
        self.selectfiles = (set(), set(), set())
        self.sleeps = []
        self.error_cb = None

    def add(self, routine, value=None, daemon=False):
        """
        Add the given coroutine to the executor

        When the routine is next resumed, value is passed to it via send() or
        throw() (see the listen() method for more details); for newly-created
        coroutines made from generator functions, this must be None. daemon
        specifies whether the coroutine should be "daemonic" or not; when no
        non-daemonic coroutines are left, the executor main loop stops.
        """
        self.routines[routine] = value
        if daemon: self.daemons.add(routine)

    def remove(self, routine):
        """
        Remove the given routine from the executor

        Returns the value the routine would have been resumed with next.

        Remark that this operation may be dangerous; other coroutines might
        wait on the routine or it might have started a nontrivial suspend,
        which would effectively raise the routine from the dead when done.
        """
        self.suspended.discard(routine)
        self.daemons.discard(routine)
        return self.routines.pop(routine, None)

    def _suspend(self, routine):
        "Suspend the given routine"
        if routine not in self.routines:
            raise RuntimeError('Suspending not-running routine')
        del self.routines[routine]
        self.suspended.add(routine)

    def _wake(self, routine, value):
        "Wake the given routine with the given value"
        if routine in self.routines:
            raise RuntimeError('Waking already-running routine')
        self.suspended.discard(routine)
        self.routines[routine] = value

    def _done(self, routine, result):
        "Clean up after this (finished) routine"
        self.remove(routine)
        return self.trigger(routine, result)

    def listen(self, event, callback):
        """
        Register callback to be invoked when the given event is triggered

        event is an arbitrary hashable object. callback is a callable that
        receives exactly one argument -- the "associated value" --, which is
        specified when the event is triggered. A particular callback may be
        registered for an event at most once. All callbacks are removed after
        an event is triggered (and must be re-added as necessary).

        The built-in triggers follow the "two return channels" convention: The
        associated value is either 2-tuple (code, value) or the None singleton
        (which is equivalent to (0, None)); code must be either 0 for a normal
        value or 1 for an exception being raised (those two are not equivalent
        given that an exception can be both used as a value and raised); value
        is an arbitrary object if code is 0 or an instance of BaseException if
        code is 1.
        """
        try:
            self.listening[event].add(callback)
        except KeyError:
            self.listening[event] = set((callback,))

    def stop_listening(self, event, callback):
        """
        Remove the given callback from event's listener set
        """
        if event not in self.listening: return
        self.listening[event].discard(callback)

    def trigger(self, event, result=None):
        """
        Trigger the given event with the given associated value

        Returns the amount of callbacks that were invoked. See listen() for a
        description of permissible associated values.
        """
        callbacks = self.listening.pop(event, ())
        for cb in callbacks:
            self._run_callback(cb, (result,), final=True)
        return len(callbacks)

    def add_select(self, file, index):
        """
        Register file to be selected upon in category index

        file is a file-like object, or a file descriptor, or anything accepted
        by the select.select() function. index is an index into the select()
        parameter list (with 0 for "reading"); see IOSuspend.SELECT_MODES for
        a mapping from mnemonic strings to valid indices.

        A file may be present in the select set for a given index at most
        once. Like listeners after triggers, selected files are removed once
        they become ready; they must be re-added if they should be waited upon
        again.
        """
        self.selectfiles[index].add(file)

    def remove_selects(self, *files):
        """
        Abort any selecting on the given files

        The given files are removed from all select sets and any listeners
        waiting for them are triggered with an EOFError exception indicating
        that the file closed (which is the case in which this method should be
        used).
        """
        self.selectfiles[0].difference_update(files)
        self.selectfiles[1].difference_update(files)
        self.selectfiles[2].difference_update(files)
        result = (1, EOFError('File closed'))
        for f in files: self.trigger(f, result)

    def _done_select(self, readable, writable, exceptable):
        "Internal helper for handling successfully select()ed files"
        self.selectfiles[0].difference_update(readable)
        self.selectfiles[1].difference_update(writable)
        self.selectfiles[2].difference_update(exceptable)
        for f in readable: self.trigger(f, (0, 'r'))
        for f in writable: self.trigger(f, (0, 'w'))
        for f in exceptable: self.trigger(f, (0, 'x'))

    def add_sleep(self, sleep):
        """
        Register the given sleep with the executor

        sleep should be an instance of the Sleep suspend (or something having
        a compatible API, i.e., having a waketime attribute as described for
        Sleep, being hashable, and having comparison operators corresponding
        to those of its waketime attribute).

        Sleeps are held in a priority queue with the sleep with the smallest
        waketime being "woken" first; they determine the timeout of the
        select() call or of a sleep() call when there are no running
        coroutines. When a sleep's waketime has passed, it is trigger()ed with
        an associated value of None.
        """
        heapq.heappush(self.sleeps, sleep)

    def _finish_sleeps(self, now=None):
        "Internal function actually waking finished sleeps"
        if now is None: now = time.time()
        while self.sleeps and self.sleeps[0].waketime <= now:
            sleep = heapq.heappop(self.sleeps)
            self.trigger(sleep)

    def on_error(self, exc, source):
        """
        Handle an otherwise unhandled error

        exc is an exception object detailing the error; source is some object
        describing the "source" of the error.

        The default implementation invokes a user-specifiable callback and
        printes a stack trace to standard error. The user-specifiable callback
        is the error_cb attribute; if it is true (instead of None), it is
        invoked with the same arguments as on_error(); if the return value of
        that is true, the printing to standard error is suppressed.
        """
        if self.error_cb and self.error_cb(exc, source): return
        lines = ['Uncaught exception in %r:\n' % (source,)]
        lines.extend(traceback.format_exception(*sys.exc_info()))
        sys.stderr.write(''.join(lines))

    def _run_callback(self, func, args, kwds=None, final=False):
        "Helper function for running a call-back."
        if kwds is None: kwds = {}
        try:
            return (0, func(*args, **kwds))
        except Exception as exc:
            if final: self.on_error(exc, func)
            return (1, exc)

    def close(self):
        """
        Close the executor and reset all of its internal state

        All running and suspended coroutines are immediately closed.
        """
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
        """
        Perform the executor's main loop

        See the class docstring for details.
        """
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

    def __call__(self, *args, **kwds):
        "Invoke this instance; an alias for run()"
        self.run(*args, **kwds)

class Lock(object):
    """
    Lock() -> new instance

    A mutex for coroutines

    Lock implements mutual exclusion of coroutines -- at most one coroutine
    can "hold" a lock at once. Use Acquire() to get hold of a lock -- which
    may block the coroutine until the lock is available -- and release() to
    release it back.

    Initially, a lock is unlocked. Locks are not reentrant -- acquiring a lock
    already held by the current coroutine will cause a deadlock.
    """

    class _Acquire(SimpleCancellable):
        "Helper class representing an Acquire() operation; see there"

        def __init__(self, parent):
            "Initializer"
            SimpleCancellable.__init__(self)
            self.parent = parent

        def apply(self, wake, executor, routine):
            "Apply this suspend; see Acquire() for details"
            if self.parent.locked:
                self.listen_reapply(wake, executor, routine, self.parent)
            else:
                self.parent.locked = True
                self.parent._trigger = lambda: executor.trigger(self.parent)
                wake((0, None))

    def __init__(self):
        "Initializer; see class docstring for details"
        self.locked = False
        self._trigger = None

    def Acquire(self):
        """
        Acquire the lock

        This will suspend the current coroutine until the lock is available if
        necessary. This returns a coroutine suspend.
        """
        return self._Acquire(self)

    def release(self):
        """
        Release the lock

        This operation never blocks. Releasing a lock that is not locked will
        cause a RuntimeError to be raised.
        """
        if not self.locked:
            raise RuntimeError('Releasing unlocked lock')
        self.locked = False
        callback, self._trigger = self._trigger, None
        callback()

class StateSwitcher(object):
    """
    StateSwitcher(state=None) -> new instance

    Manage a variable transitioning between different values

    A StateSwitcher encloses a state variable and provides methods for waiting
    until it reaches a particular value, changing its value, and an atomical
    compare-and-swap operation.

    The "state" constructor parameter sets the initial state; the state can be
    queried using the same-named instance attribute, but should be assigned
    using the Set() method.
    """

    class _Wait(SimpleCancellable):
        "Helper class representing a Wait() operation; see there"

        def __init__(self, parent, match):
            "Initializer"
            SimpleCancellable.__init__(self)
            self.parent = parent
            self.match = match

        def apply(self, wake, executor, routine):
            "Apply this suspend; see Wait() for details"
            if self.parent.state == self.match:
                wake((0, None))
            else:
                self.listen_reapply(wake, executor, routine, self.parent)

    class _Toggle(SimpleCancellable):
        "Helper class representing a Toggle() operation; see there"

        def __init__(self, parent, match, new, wait):
            "Initializer"
            SimpleCancellable.__init__(self)
            self.parent = parent
            self.match = match
            self.new = new
            self.wait = wait

        def apply(self, wake, executor, routine):
            "Apply this suspend; see Toggle() for details"
            if self.parent.state == self.match:
                self.parent.state = self.new
                executor.trigger(self.parent, (0, self.new))
                wake((0, True))
            elif not self.wait:
                wake((0, False))
            else:
                self.listen_reapply(wake, executor, routine, self.parent)

    class _Set(Suspend):
        "Helper class representing a Set() operation; see there"

        def __init__(self, parent, new):
            "Initializer"
            self.parent = parent
            self.new = new

        def apply(self, wake, executor, routine):
            "Apply this suspend; see Set() for details"
            prev_state = self.parent.state
            self.parent.state = self.new
            executor.trigger(self.parent, (0, self.new))
            wake((0, prev_state))

    def __init__(self, state=None):
        "Initializer; see class docstring for details"
        self.state = state

    def Wait(self, match):
        """
        Wait until the state variable matches the given value

        This returns a coroutine suspend.
        """
        return self._Wait(self, match)

    def Toggle(self, match, new, wait=False):
        """
        If the state variable is equal to match, set it to new

        The comparison and the setting happen atomically. If wait is true,
        waits until the state variable is equal to match. This returns a
        coroutine suspend; when used, the suspend returns whether the
        operation succeeded (this always happens if wait is true).
        """
        return self._Toggle(self, match, new, wait)

    def Set(self, new):
        """
        Set the state variable to the given new value

        This returns a coroutine suspend.
        """
        return self._Set(self, new)

class BinaryLineReader(object):
    """
    BinaryLineReader(file, encoding=None, errors=None) -> new instance

    Convenience object for reading lines in a coroutine-safe way

    file is a file to read from; it must be open in binary mode. encoding and
    errors, if not None, indicate that individual lines (!) should be decoded
    with the given encoding and error handling mode.

    Data are read in (binary) chunks whose size is defermined by the
    chunk_size instance attribute; it defaults to the LINEREADER_CHUNK_SIZE
    module-level constant.

    Line splitting is performed on line feed (0x0A) bytes *before* data are
    decoded. This class avoids the potential blocking of file object methods
    like readline(), which would block the entire coroutine executor by
    extension.
    """

    class _ReadLine(Suspend):
        "Helper class for ReadLine(); see there"

        def __init__(self, parent):
            "Initializer"
            self.parent = parent
            self._delegate = None

        def apply(self, wake, executor, routine):
            "Apply this suspend; see ReadLine() for details"
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
                                      self.parent.chunk_size)
            self._delegate.apply(inner_wake, executor, routine)

        def cancel(self):
            if self._delegate is not None:
                self._delegate.cancel()

    def __init__(self, file, encoding=None, errors=None):
        "Initializer; see class docstring for details"
        self.file = file
        self.encoding = encoding
        self.errors = errors
        self.chunk_size = LINEREADER_CHUNK_SIZE
        self._buffer = b''
        self._eof = False

    def _extract_line(self):
        "Helper: Extract (and decode) a line from the buffer or return None"
        line, sep, rest = self._buffer.partition(b'\n')
        if not sep and not self._eof:
            return None
        self._buffer = rest
        line += sep
        if self.encoding is not None:
            line = line.decode(self.encoding, self.errors)
        return line

    def ReadLine(self):
        """
        Read a single line

        This returns a coroutine suspend, which returns the (possibly decoded)
        line when finished.
        """
        return self._ReadLine(self)

def sigpipe_handler(rfd, waits, cleanup):
    """
    Helper coroutine for set_sigpipe()

    This is expected to do the following things in a loop:
    - Read from rfd.
    - Invoke the poll() method of every object in waits (which is a set) and
      discard those objects whose poll() method does not return None.
    - Employ os.waitpid() to wait for any stray child processes.
    - Trigger ProcessTag-s constructed from the PID-s of all finished child
      processes with the respective exit codes as associated values.
    When the coroutine is terminated, it is expected to invoke the cleanup()
    callback (which expects no paramaters).
    """
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
    """
    Install a signal wake-up pipe into the given executor

    Invoking this function on an executor enables coroutines inside the
    executor to wait for child processes. The global (!) configuration
    associated with that is undone when the executor is closed.

    coroutine is instantiated with the parameters elaborated upon in
    sigpipe_handler() and run daemonically in the executor.

    This installs a do-nothing handler for the SIGCHLD signal (the handler
    in place previously is reinstated when the executor is closed);
    additionally, signal.set_wakeup_fd() is used to wake up the executor's
    select loop whenever a signal arrives. Since these are global state, this
    is a module-level function. The executor gains a "waits" attribute, which
    is a set of subprocess.Popen instances to be regularly polled for having
    finished by coroutine.
    """
    def cleanup(reset_signals=True):
        "Callback for cleaning up when the executor is shut down"
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
    """
    A coroutine that immediately returns the given value
    """
    yield coroutines.Exit(value)

def const_raise(exc, excclass=RuntimeError):
    """
    A coroutine that immediately raises the given exception

    If exc is not actually an exception, excclass is instantiated using it
    as the only parameter and raised.
    """
    if not isinstance(exc, BaseException):
        raise excclass(exc)
    yield exc

def run(routines=(), main=None, sigpipe=False, on_error=None):
    """
    Convenience function for running a coroutine executor

    routines is an iterable of coroutines to run. main (if not None) is a
    suspend (!) to be applied in an additional coroutine; its return value is
    returned from run(). If sigpipe is true, set_sigpipe() is applied to the
    executor prior to starting it. on_error, if not None, is installed as the
    executor's error_cb.
    """
    def main_routine():
        "Wrapper coroutine for the main suspend"
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
