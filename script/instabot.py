
# -*- coding: ascii -*-

"""
A bot library for Instant.
"""

import sys, os, io, re, time, stat
import traceback
import collections, heapq, ast
import calendar
import json
import socket
import threading
import shutil

import websocket_server

try:
    from queue import Queue, Empty as QueueEmpty
except ImportError:
    from Queue import Queue, Empty as QueueEmpty

VERSION = 'v1.5.6'

RELAXED_COOKIES = bool(os.environ.get('INSTABOT_RELAXED_COOKIES'))

_unicode = websocket_server.compat.unicode

class Canceller(object):
    """
    Canceller() -> new instance

    A means of cooperatively cancelling some asynchronous operation.

    Until cancel() is invoked, the Canceller is "active"; after that, it is
    "cancelled".
    """

    def __init__(self):
        "Instance initializer; see the class docstring for details."
        self.event = threading.Event()

    def active(self):
        """
        Return whether the canceller is active.
        """
        return not self.event.isSet()

    def wait(self, timeout):
        """
        Wait for the specified time (in seconds) or until cancelled.

        Returns whether the canceller was still active at the moment where
        the wait ended (remark, however, that this could change immediately
        after).
        """
        return not self.event.wait(timeout)

    def cancel(self):
        """
        Cancel the canceller.

        After this, active() will return False, and wait() will return False
        immediately.
        """
        self.event.set()

class EventScheduler(object):
    """
    EventScheduler(time=None, sleep=None) -> new instance

    An EventScheduler executes callbacks at dynamically specified times.

    time is a function that returns the current time as a floating-point
    number; the default implementation returns the current UNIX time. sleep
    is a function that takes a single floating-point argument or None, blocks
    the calling thread for that time (where None means "arbitrarily long"),
    and returns whether there is anything to be done in the EventScheduler's
    queue; the default implementation does this in such a way that it can be
    interrupted by concurrent callback submissions.

    As the requirements suggest, specifying a non-default implementation for
    sleep would be unwise; therefore, time should use the same time units as
    the default sleep, i.e. seconds.

    Callbacks to be run are added to the EventScheduler via add(), add_abs(),
    and add_now(); these return instances of the enclosed Event class that can
    be passed to the cancel() method (of this class) in order to prevent
    them from running (before they have started).

    The actual execution of the callbacks is dispatched by the run() method
    (or its run_once() relative); the method returns whenever there are no
    pending callbacks in the scheduelr and its "forever" flag (which defaults
    to being set; see set_forever()) is cleared. If a thread wants to wait
    for an EventScheduler executing in another thread to finish, it can use
    the join() method for that.

    See also the standard library's "sched" module for similar (but not
    well-suited for concurrent scenarios) functionality.
    """
    class Event:
        """
        Event(time, seq, callback) -> new instance

        An object enclosing a function to be executed by an EventScheduler.

        time is the time at which the callback is to be run; seq is a value
        used to disambiguate events with the same time (e.g. a sequentially
        increasing integer); callback is the actual function to run (taking
        no arguments and with the return value ignored).

        Event objects can be called and forward these calls to their
        callbacks; additionally, comparing two Event objects yields the same
        result as comparing their (time, seq) tuples.
        """
        def __init__(self, time, seq, callback):
            "Instance initializer; see the class docstring for details."
            self.time = time
            self.sortkey = (time, seq)
            self.callback = callback
            self.handled = False
            self.canceled = False
        def __call__(self):
            "Calling protocol support; see the class docstring for details."
            self.callback()
        def __gt__(self, other):
            "Comparison support; see the class docstring for details."
            return self.sortkey > other.sortkey
        def __ge__(self, other):
            "Comparison support; see the class docstring for details."
            return self.sortkey >= other.sortkey
        def __eq__(self, other):
            "Comparison support; see the class docstring for details."
            return self.sortkey == other.sortkey
        def __ne__(self, other):
            "Comparison support; see the class docstring for details."
            return self.sortkey != other.sortkey
        def __le__(self, other):
            "Comparison support; see the class docstring for details."
            return self.sortkey <= other.sortkey
        def __lt__(self, other):
            "Comparison support; see the class docstring for details."
            return self.sortkey < other.sortkey
    def __init__(self, time=None, sleep=None):
        "Instance initializer; see the class docstring for details."
        if time is None: time = self._time
        if sleep is None: sleep = self._sleep
        self.pending = []
        self.time = time
        self.sleep = sleep
        self.forever = True
        self.running = False
        self.cond = threading.Condition()
        self._seq = 0
    def __enter__(self):
        "Context manager entry; internal."
        return self.cond.__enter__()
    def __exit__(self, *args):
        "Context manager exit; internal."
        return self.cond.__exit__(*args)
    def _time(self):
        "Internal: Default implementation of the time callback."
        return time.time()
    def _sleep(self, delay):
        "Internal: Default implementation of the sleep callback."
        with self:
            self.cond.wait(delay)
            return bool(self.pending)
    def add_abs(self, timestamp, callback):
        """
        Schedule callback to be invoked at timestamp and return an Event
        object representing the registration.

        The Event can be passed to cancel() to cancel the callback's execution
        (before it starts). See the class docstring for time unit details.
        """
        with self:
            evt = self.Event(timestamp, self._seq, callback)
            self._seq += 1
            heapq.heappush(self.pending, evt)
            self.cond.notifyAll()
            return evt
    def add(self, delay, callback):
        """
        Schedule callback to be invoked in delay time units and return an
        Event object representing the registration.

        See the notes for add_abs() for more details.
        """
        return self.add_abs(self.time() + delay, callback)
    def add_now(self, callback):
        """
        Schedule callback to be invoked as soon as possible.

        Like add_abs() and add(), this returns an Event object representing
        the registration, but actually cancelling the event may be hard. See
        also the notes for add_abs() for additional details.
        """
        return self.add_abs(self.time(), callback)
    def cancel(self, event):
        """
        Attempt to cancel the given Event's callback's execution and return
        whether that was successful.

        An Event can only be cancelled if its callback has not started
        executing yet.
        """
        with self:
            event.canceled = True
            ret = (not event.handled)
            self.cond.notifyAll()
            return ret
    def clear(self):
        """
        Unconditionally remove all pending callbacks.

        As a side effect and depending on the forever flag, concurrent
        invocation of the run() method may return.
        """
        with self:
            self.pending[:] = []
            self.cond.notifyAll()
    def set_forever(self, v):
        """
        Set whether this EventScheduler should wait for additional tasks when
        there are none queued.

        If this is cleared while there are no queued tasks, the Scheduler may
        shut down as a side effect. If this is cleared while there *are*
        pending tasks, they will be given a chance to execute (and potentially
        to spawn new tasks ad infinitum).
        """
        with self:
            self.forever = v
            self.cond.notifyAll()
    def shutdown(self):
        "A convenience alias for set_forever(False)."
        self.set_forever(False)
    def join(self):
        """
        Wait until a concurrent invocation of the run() method returns.
        """
        with self:
            while self.running:
                self.cond.wait()
    def on_error(self, exc):
        """
        Error handling callback.

        When an exception is raised in a callback, this method is invoked in
        the exception handler with the caught exception object as the only
        argument. sys.exc_info() may be inspected.

        The default implementation re-raises the exception.
        """
        raise
    def run_once(self, hangup=True):
        """
        Execute all currently pending callbacks and wait until it would be
        time to run the next one.

        If hangup is true and there are no callbacks to run, this will wait
        indefinitely.

        Because of the somewhat backwards interface, this method may be of
        little use except as a part of run().
        """
        wait = None
        while 1:
            with self:
                if not self.pending: break
                now = self.time()
                head = self.pending[0]
                if head.time > now and not head.canceled:
                    wait = head.time - now
                    break
                heapq.heappop(self.pending)
                head.handled = True
                if head.canceled: continue
            try:
                head()
            except Exception as exc:
                self.on_error(exc)
        if wait is None and not hangup: return False
        return self.sleep(wait)
    def run(self):
        """
        Execute all currently pending and future callbacks.

        Whenever the forever flag is cleared and there are no pending
        callbacks, this method returns.
        """
        try:
            with self:
                self.running = True
            while 1:
                f = self.forever
                if not self.run_once(f) and not f: break
        finally:
            with self:
                self.running = False
                self.cond.notifyAll()

def backoff_linear(counter):
    """
    Linear (connection attempt) backoff implementation.

    For the given (zero-based) failed connection attempt counter, this
    returns the time (in possibly fractional seconds) to wait before trying
    again.

    This implementation returns the value of counter unchanged (in particular,
    the second connection attempt is done immediately after the first).
    """
    return counter

class AtomicSequence(object):
    """
    AtomicSequence() -> new instance

    An atomic (i.e. thread-safe) counter.

    Instances of this class are callable; they expect no arguments and return
    a single integer which is the current value of the counter. Subsequent
    calls return increasing integers.

    Counting starts at zero: The first call of a new instance will return 0,
    the next will return 1, etc.
    """
    def __init__(self):
        "Instance initializer; see the class docstring for details."
        self.value = -1
        self._lock = threading.Lock()
    def __call__(self):
        "Calling protocol support; see the class docstring for details."
        with self._lock:
            self.value += 1
            return self.value

class InstantClient(object):
    """
    InstantClient(url, *, backoff=BACKOFF, keepalive=False, **kwds)
        -> new instance

    Generic Instant API endpoint wrapper.

    url       is the URL of the API endpoint.
    backoff   is a backoff strategy for repeatedly failing connection
              attempts; it defaults to the BACKOFF class attribute, which, in
              turn defaults to the module-level backoff_linear() function. See
              the latter for more details.
    keepalive indicates whether the client should reconnect when its
              connection breaks.

    The following keyword-only arguments are passed on to the underlying
    WebSocket connect() call:
    timeout   : The connection timeout (a floating-point amount of seconds or
                None for no timeout); default to the TIMEOUT class attribute.
    cookies   : A CookieJar instance (or None) for cookie management; defaults
                to the COOKIES class attribute.
    ssl_config: A mapping of SSL configuration values, or None for default SSL
                settings. May have the following (string) keys:
                cert: A client certificate file (in PEM format).
                key : The private key corresponding to cert (in PEM format; if
                      omitted, the private key is taken from the cert file).
                ca  : A list of CA certificates to trust (exclusively; in PEM
                      format).

    The following keyword-only argument configures the same-named instance
    attribute:
    logger: A Logger instance used to record key events in the bot's
            lifecycle. Defaults to the NULL_LOGGER.

    Unrecognized keyword arguments are ignored.

    The following groups of methods are provided:
    - The underlying connection can be managed via connect() and close();
      additionally, the keepalive attribute (initialized from the
      corresponding constructor parameter) is relevant.
    - Synchronous sending/receiving can be done via send_*() and recv().
    - Generic connection events as well as certain received data are handled
      by on_*() methods.
    - Specific messages received from the backend are processed by handle_*()
      methods; see in particular on_message() for details on how the default
      implementations dispatch further calls.
    - A main loop managing reconnects, message reception, and event handler
      dispatch is invoked via run() or put into a background thread via
      start().
    """
    TIMEOUT = None
    COOKIES = None
    BACKOFF = staticmethod(backoff_linear)
    def __init__(self, url, **kwds):
        "Instance initializer; see the class docstring for details."
        self.url = url
        self.timeout = kwds.get('timeout', self.TIMEOUT)
        self.cookies = kwds.get('cookies', self.COOKIES)
        self.ssl_config = kwds.get('ssl_config', None)
        self.backoff = kwds.get('backoff', self.BACKOFF)
        self.keepalive = kwds.get('keepalive', False)
        self.logger = kwds.get('logger', NULL_LOGGER)
        self.ws = None
        self.sequence = AtomicSequence()
        self._wslock = threading.RLock()
    def connect(self):
        """
        Create a connection to the stored URL and return it.

        If there already is an active connection, it is returned without
        creating a new one.
        """
        with self._wslock:
            if self.ws is not None: return self.ws
            self.logger.log('CONNECT url=%r' % (self.url,))
            jar = self.cookies
            self.ws = websocket_server.client.connect(self.url,
                cookies=jar, timeout=self.timeout, ssl_config=self.ssl_config)
            if isinstance(jar, websocket_server.cookies.FileCookieJar):
                jar.save()
        return self.ws
    def on_open(self):
        """
        Event handler method invoked when the connection opens.

        The default implementation merely produces a logging message.
        """
        self.logger.log('OPENED')
    def on_message(self, rawmsg):
        """
        Event handler method invoked when a text frame arrives via the
        Websocket.

        rawmsg is the payload of the frame.

        The default implementation decodes the frame as JSON and dispatches
        to one of the handle_*() methods (if the "type" field of the message
        is in a fixed known whitelist) or on_unknown() (otherwise).

        The handle_*() methods and on_unknown() take the same arguments,
        namely the JSON-decoded contents of the frame (typically a dictionary)
        and its raw string form as passed to this method.

        As default, handle_unicast() and handle_broadcast() dispatch the
        "data" field of the received message to on_client_message(); the
        other handle_*() methods do nothing as default (unless, of course,
        they are overridden).
        """
        content = json.loads(rawmsg)
        msgt = content.get('type')
        func = {
            'identity': self.handle_identity, 'pong': self.handle_pong,
            'joined': self.handle_joined, 'who': self.handle_who,
            'unicast': self.handle_unicast,
            'broadcast': self.handle_broadcast,
            'response': self.handle_response, 'left': self.handle_left,
            'error': self.handle_error
        }.get(msgt, self.on_unknown)
        func(content, rawmsg)
    def on_frame(self, msgtype, content, final):
        """
        Event handler method invoked when a binary frame arrives via the
        WebSocket.

        msgtype is the WebSocket opcode of the frame, content is its payload,
        and final indicates whether this is a partial frame (by being False;
        since the default recv() operates in non-streaming mode, final is
        usually always True).

        The default implementation does nothing.
        """
        pass
    def on_connection_error(self, exc):
        """
        Event handler method invoked when connect() raises an exception.

        exc is the exception object. sys.exc_info() may be inspected.

        The default implementation reraises the exception unless the keepalive
        attribute is true.
        """
        self.logger.log_exception('ERROR', exc)
        if not self.keepalive: raise
    def on_timeout(self, exc):
        """
        Event handler method invoked when the underlying connection times out
        while reading.

        exc is the exception object. sys.exc_info() may be inspected.

        Because the underlying standard library I/O buffers may become
        inconsistent when timeouts happen, the connection is always destroyed
        after this is called. The default implementation re-raises the
        exception unconditionally, effectively handing it off to on_error().
        """
        self.logger.log_exception('TIMEOUT', exc)
        raise
    def on_error(self, exc):
        """
        Event handler method invoked when a general exception happens during
        the main loop.

        exc is the exception object. sys.exc_info() may be inspected.

        The default implementation re-raises the exception, causing a calling
        run() to abort.
        """
        self.logger.log_exception('ERROR', exc)
        raise
    def on_close(self, final):
        """
        Event handler method invoked when the underlying connection has
        closed.

        final indicates whether a reconnect is about to happen (False) or
        whether the close is really final (True).

        The default implementation merely produces a logging message.
        """
        self.logger.log('CLOSED final=%r' % (final,))
    def handle_identity(self, content, rawmsg):
        """
        Event handler method for "identity" API messages.

        See on_message() for details.
        """
        pass
    def handle_pong(self, content, rawmsg):
        """
        Event handler method for "pong" API messages.

        See on_message() for details.
        """
        pass
    def handle_joined(self, content, rawmsg):
        """
        Event handler method for "joined" API messages.

        See on_message() for details.
        """
        pass
    def handle_who(self, content, rawmsg):
        """
        Event handler method for "who" API messages.

        See on_message() for details.
        """
        pass
    def handle_unicast(self, content, rawmsg):
        """
        Event handler method for "unicast" API messages.

        See on_message() for details.
        """
        self.on_client_message(content['data'], content, rawmsg)
    def handle_broadcast(self, content, rawmsg):
        """
        Event handler method for "broadcast" API messages.

        See on_message() for details.
        """
        self.on_client_message(content['data'], content, rawmsg)
    def handle_response(self, content, rawmsg):
        """
        Event handler method for "response" API messages.

        See on_message() for details.
        """
        pass
    def handle_left(self, content, rawmsg):
        """
        Event handler method for "left" API messages.

        See on_message() for details.
        """
        pass
    def handle_error(self, content, rawmsg):
        """
        Event handler method for "error" API messages.

        See on_message() for details.
        """
        pass
    def on_unknown(self, content, rawmsg):
        """
        Event handler method for unrecognized API messages.

        See on_message() for details.
        """
        pass
    def on_client_message(self, data, content, rawmsg):
        """
        Event handler method for received inter-client messages.

        data is the client-specified message payload, conventionally a
        dictionary whose "type" entry allows determining its exact purpose;
        content is the backend message enclosing data and contains additional
        metadata (e.g. the sender and the timestamp); rawmsg is the original
        character string as received from the WebSocket.

        The default implementation does nothing.
        """
        pass
    def recv(self):
        """
        Receive and return a single text frame from the underlying WebSocket.

        If there is currently no connection or an EOF is received, this
        returns None. Non-text frames are processed synchronously via
        on_frame(). When a text frame is received, this returns its textual
        content.
        """
        ws = self.ws
        if ws is None: return None
        while 1:
            frame = ws.read_frame()
            if frame is None: return None
            if frame.msgtype != websocket_server.OP_TEXT:
                self.on_frame(frame.msgtype, frame.content, frame.final)
                continue
            return frame.content
    def send_raw(self, rawmsg):
        """
        Send the given text into the underlying WebSocket (nearly) unmodified.

        The input is coerced into a Unicode string; aside from that, no
        transformations are performed. If there is no connection, this raises
        a websocket_server.ConnectionClosedError.

        The default implementation takes only one argument; other ones could
        accept additional (keyword-only) ones.
        """
        ws = self.ws
        if ws is None: raise websocket_server.ConnectionClosedError
        ws.write_text_frame(_unicode(rawmsg))
    def send_seq(self, content, **kwds):
        """
        Augment the given data object with a unique sequence number and send
        its JSON serialization into the underlying WebSocket.

        The sequence number is stored in the "seq" entry of content in-place
        and returned; it is unique from every other sequence number generated
        by this InstantClient instance (via this method).

        Additional keyword-only arguments can be interpreted by overridden
        versions of this method and should be passed on to send_raw() as
        appropriate.
        """
        seq = self.sequence()
        content['seq'] = seq
        self.send_raw(json.dumps(content, separators=(',', ':')), **kwds)
        return seq
    def send_unicast(self, dest, data, **kwds):
        """
        Send a unicast API message to the indicated destination with the given
        data.

        This is a convenience wrapper around send_seq({'type': 'unicast',
        'to': dest, 'data': data}, **kwds).
        """
        return self.send_seq({'type': 'unicast', 'to': dest, 'data': data},
                             **kwds)
    def send_broadcast(self, data, **kwds):
        """
        Send a broadcast API message with the given data.

        This is a convenience wrapper around send_seq({'type': 'broadcast',
        'data': data}, **kwds).
        """
        return self.send_seq({'type': 'broadcast', 'data': data}, **kwds)
    def send_to(self, dest, data, **kwds):
        """
        Send a unicast or broadcast message with the given data.

        If dest is None, the message is a broadcast, otherwise it is a unicast
        directed to dest. Additional keyword-only arguments are forwarded to
        send_broadcast()/send_unicast().
        """
        if dest is None:
            return self.send_broadcast(data, **kwds)
        else:
            return self.send_unicast(dest, data, **kwds)
    def close(self, final=True):
        """
        Close the underlying WebSocket connection.

        If final is true, this clears the keepalive attribute of this instance
        to ensure that its main loop does not attempt to reconnect.
        """
        with self._wslock:
            if final: self.keepalive = False
            if self.ws is not None: self.ws.close()
            self.ws = None
    def run(self, connect_canceller=None):
        """
        The main loop of an InstantClient.

        connect_canceller, if not None, is a Canceller instance that can be
        used to cancel connection attempts performed by this invocation;
        however, cancelling it does not close the WebSocket connection if it
        is established.

        This takes care of (re)connecting, backing off on failing connection
        attempts, message reading, and connection closing. Most on_*() methods
        are dispatched from here.
        """
        if connect_canceller is None: connect_canceller = Canceller()
        while 1:
            connected, reconnect = False, 0
            while connect_canceller.active():
                try:
                    self.connect()
                except Exception as exc:
                    self.on_connection_error(exc)
                    connect_canceller.wait(self.backoff(reconnect))
                    reconnect += 1
                else:
                    connected = True
                    break
            if not connected:
                break
            try:
                self.on_open()
                while 1:
                    try:
                        rawmsg = self.recv()
                    except socket.timeout as exc:
                        self.on_timeout(exc)
                        break
                    if rawmsg is None:
                        break
                    elif not rawmsg:
                        continue
                    self.on_message(rawmsg)
            except websocket_server.ConnectionClosedError:
                # Server-side timeouts cause the connection to be dropped.
                pass
            except Exception as exc:
                self.on_error(exc)
            finally:
                final = not self.keepalive
                try:
                    self.close(final)
                except Exception as exc:
                    self.on_error(exc)
                finally:
                    self.on_close(final)
            if final: break
    def start(self, *args, **kwds):
        """
        Create a daemonic background thread running run() and return it.

        The thread is already started when this returns.
        """
        thr = threading.Thread(target=self.run, args=args, kwargs=kwds)
        thr.setDaemon(True)
        thr.start()
        return thr

class Bot(InstantClient):
    """
    Bot(url, nickname=Ellipsis, **kwds) -> new instance

    An InstantClient that maintains a nickname and allows submitting posts.

    url is the WebSocket URL to connect to (typically corresponding to an
    Instant room); nickname is the nickname to use (either a string or None,
    with a None nick rendering the bot invisible (in contrast to an empty
    string as a nick)), defaulting to the NICKNAME class attribute.

    This class can be used as a superclass for complex bots; see also HookBot
    for convenience functionality for simpler ones.
    """
    NICKNAME = None
    def __init__(self, url, nickname=Ellipsis, **kwds):
        "Instance initializer; see the class docstring for details."
        if nickname is Ellipsis: nickname = self.NICKNAME
        InstantClient.__init__(self, url, **kwds)
        self.nickname = nickname
        self.identity = None
        self._nicklock = threading.RLock()
    def on_timeout(self, exc):
        """
        Connection timeout event handler.

        This implementation overrides the behavior from InstantClient by only
        re-raising the exception if there is no connection timeout configured.

        See the base class implementation for more details.
        """
        if self.timeout is not None:
            self.logger.log('TIMEOUT')
            return
        InstantClient.on_timeout(self, exc)
    def handle_identity(self, content, rawmsg):
        """
        "identity" API message handler.

        This implementation stores the "data" member of the received object
        in the "identity" instance attribute and, taking this to be the start
        of the bot's active lifetime, invokes send_nick() to announce the
        bot's nickname.

        See the base class implementation for more details.
        """
        self.identity = content['data']
        self.send_nick()
    def on_client_message(self, data, content, rawmsg):
        """
        Client-to-client message handler.

        This implementation checks whether the received message is a nickname
        query and responds to it using send_nick() if so.

        See the base class implementation for more details.
        """
        peer = content['from']
        if data.get('type') == 'who' and peer != self.identity['id']:
            self.send_nick(peer)
    def send_nick(self, peer=None):
        """
        Announce this bot's nickname to the given peer or everyone.

        Unless None, peer is the ID of a client to send the announcement to;
        if peer is None, the announcement is broadcast. If this bot's nickname
        is configured to None, no announcement is sent.
        """
        with self._nicklock:
            if self.nickname is None: return
            data = {'type': 'nick', 'nick': self.nickname,
                    'uuid': self.identity['uuid']}
            self.send_to(peer, data)
    def send_post(self, text, parent=None, nickname=Ellipsis):
        """
        Send a chat post.

        text is the content of the post (as a string); parent is the ID of the
        post the new post is a response to (or None to create a new top-level
        post); nickname (if not Ellipsis) allows changing the bot's nickname
        along with the post.
        """
        data = {'type': 'post', 'text': text}
        if parent is not None:
            data['parent'] = parent
        with self._nicklock:
            if nickname is not Ellipsis:
                self.nickname = nickname
            data['nick'] = self.nickname
            self.logger.log('SENDPOST parent=%r nick=%r text=%r' % (parent,
                data['nick'], text))
            return self.send_broadcast(data)

class HookBot(Bot):
    """
    HookBot(url, nickname=Ellipsis, *, init_cb=None, open_cb=None,
            post_cb=None, close_cb=None, **kwds) -> new instance

    An extension of Bot that provides externally settable callbacks for key
    events.

    All callbacks take the HookBot instance as the first positional argument
    and are stored in same-named instance attributes; aside from that, their
    signatures and places of invocation differ:
    - init_cb(self) -> None
      Invoked by the HookBot constructor. Allow storing additional state in
      instance attributes.
    - open_cb(self) -> None
      Invoked when a new connection is established. In contrast to init_cb(),
      this may be called multiple times over the lifetime of a HookBot.
    - post_cb(self, post, meta) -> str or None
      Invoked when a new post is submitted. post is the payload of the
      client-to-client message carrying the post, enriched with additional
      information (in-place) by the caller; it has the following entries:
      type     : The string 'post'.
      text     : The textual content of the post.
      nick     : The nickname of the sender (as used for this post).
      timestamp: The timestamp at which the backend processed the post, in
                 milliseconds since the UNIX Epoch.
      id       : A unique ID of the post (as a string).
      from     : The ID of the sender (as a string).
      meta is a dictionary containing additional less-relevant information:
      content: The API message via which the post arrived at the HookBot.
      rawmsg : The textual representation of content as it arrived over the
               wire (aside from UTF-8 decoding).
      reply  : A closure that, when invoked with a single positional argument,
               submits a reply to the post being handled (when it was
               constructed) with the only argument as the reply's text. See
               send_post() for more details.
      The return value of post_cb(), if not None, is sent as a reply to the
      post being handled as if it had been passed to meta['reply'].
    - close_cb(self, final) -> None
      Invoked when a connection is about to be closed. final tells whether
      the close will be followed by a reconnect (final is false) or not (final
      is true).
    """
    def __init__(self, url, nickname=Ellipsis, **kwds):
        "Instance initializer; see the class docstring for details."
        Bot.__init__(self, url, nickname, **kwds)
        self.init_cb = kwds.get('init_cb')
        self.open_cb = kwds.get('open_cb')
        self.post_cb = kwds.get('post_cb')
        self.close_cb = kwds.get('close_cb')
        if self.init_cb is not None: self.init_cb(self)
    def on_open(self):
        """
        Connection opening event handler.

        This implementation invokes the corresponding callback, if any; see
        the class docstring for details.
        """
        Bot.on_open(self)
        if self.open_cb is not None: self.open_cb(self)
    def on_client_message(self, data, content, rawmsg):
        """
        Client-to-client message reception handler.

        This implementation invokes the corresponding callback, if any; see
        the class docstring for details.
        """
        Bot.on_client_message(self, data, content, rawmsg)
        if data.get('type') == 'post' and self.post_cb is not None:
            post = dict(data, timestamp=content['timestamp'],
                id=content['id'], **{'from': content['from']})
            reply = lambda text: self.send_post(text, content['id'])
            res = self.post_cb(self, post, {'content': content,
                                            'rawmsg': rawmsg,
                                            'reply': reply})
            if res is not None: reply(res)
    def on_close(self, final):
        """
        Connection closing event handler.

        This implementation invokes the corresponding callback, if any; see
        the class docstring for details.
        """
        Bot.on_close(self, final)
        if self.close_cb is not None: self.close_cb(self, final)

class LogHandler:
    """
    Abstract class: A Logger backend sending formatted messages somewhere.
    """
    def emit(self, text, timestamp):
        """
        Process the given log message.

        text is the (fully formatted) log message (excluding trailing
        newlines); timestamp is an easily accessible copy of the message's
        timestamp (expressed as fractional seconds since the UNIX epoch).
        """
        raise NotImplementedError
    def close(self):
        """
        Clean up any resources held by this handler.

        The default implementation does nothing.
        """
        pass
    def read_back(self):
        """
        Return an iterable of all past log lines, where applicable.

        The lines are formatted similarly to those passed to emit(), and, in
        particular, have no trailing newlines.

        The default implementation raises a RuntimeError.
        """
        raise RuntimeError('Read-back not supported')

class StreamLogHandler(LogHandler):
    """
    StreamLogHandler(stream, autoflush=True, *, read_stream=None)
        -> new instance

    A Logger backend that writes messages to the given stream, or discards
    them if the stream is None.

    stream is the stream to write to; autoflush specifies whether the stream
    should be flushed after every write; read_stream is the stream to read
    logs back from (if None, this is resolved to stream on every read_back()
    call).
    """
    def __init__(self, stream, autoflush=True, read_stream=None):
        "Instance initializer; see the class docstring for details."
        self.stream = stream
        self.autoflush = autoflush
        self.read_stream = read_stream
    def emit(self, text, timestamp):
        """
        Process the given log message.

        See the base class' method for interface details.

        Unless the underlying stream is None, this implementation appends a
        newline to the text, writes it to the underlying stream, and (if
        enabled) flushes the stream.
        """
        if self.stream is None: return
        self.stream.write(text + '\n')
        if self.autoflush: self.stream.flush()
    def read_back(self):
        """
        Read log lines from the stream and yield them.

        See the base class' method for interface details.

        This reads from the read_stream instance attribute, or, if that is
        None, from the stream also used for writing. If the chosen stream is
        not readable, this raises a RuntimeError.
        """
        stream = self.stream if self.read_stream is None else self.read_stream
        if not stream.readable():
            raise RuntimeError('Cannot read-back: Stream not readable')
        for line in stream:
            yield line.rstrip('\n')

class FileLogHandler(LogHandler):
    """
    FileLogHandler(filename, autoflush=True) -> new instance

    A Logger backend that writes to a named file.

    filename is the name of the log file; autoflush specifies whether written
    data should be passed to the OS after every message.
    """
    def __init__(self, filename, autoflush=True):
        "Instance initializer; see the class docstring for details."
        self.filename = filename
        self.autoflush = autoflush
        self.file = open(filename, 'a+')
    def emit(self, text, timestamp):
        """
        Process the given log message.

        See the base class' method for interface details.
        """
        self.file.write(text + '\n')
        if self.autoflush: self.file.flush()
    def close(self):
        """
        Clean up any resources held by this handler.
        """
        old_file, self.file = self.file, sys.stderr
        old_file.close()
    def read_back(self):
        """
        Read log lines back from the file and yield them.

        See the base class' method for interface details.

        Particular care should be taken to ensure the returned generator is
        properly cleaned up (e.g. by being exhausted), as it seeks back to the
        end of the file when done.
        """
        fp = self.file
        if not fp.seekable():
            raise RuntimeError('Cannot read-back: File not seekable')
        fp.seek(0)
        try:
            for line in fp:
                yield line.rstrip('\n')
        finally:
            fp.seek(0, os.SEEK_END)

class RotatingFileLogHandler(FileLogHandler):
    """
    FileLogHandler(filename, granularity='X', autoflush=True) -> new instance

    A log handler that writes to files and regularly changes the files it
    writes to.
    """
    TIME_SUFFIX_RE = re.compile('^\.[0-9-]+$')
    @classmethod
    def parse_cli_config(cls, arg):
        """
        Parse the given given command-line argument into keyword arguments to
        pass to the constructor.
        """
        granularity, sep, compression = arg.partition(':')
        if not granularity: granularity = 'X'
        if not compression: compression = None
        return {'granularity': granularity, 'compression': compression}
    @classmethod
    def _parse_compression(cls, label):
        """
        Internal: Validate a compression scheme and prepare for using it.
        """
        if label in (None, 'none'):
            return None
        elif label in ('gz', 'gzip'):
            import gzip as compressor
            label = 'gz'
        elif label in ('bz2', 'bzip2'):
            import bz2 as compressor
            label = 'bz2'
        elif label == 'lzma':
            import lzma as compressor
        else:
            raise ValueError('Unrecognized compression scheme: %s '
                                 '(must be one of gz, bz2, lzma)' % (label,))
        return (label, lambda filename: compressor.open(filename, 'wt'),
                       lambda filename: compressor.open(filename, 'rt'))
    @classmethod
    def _rotation_params(cls, filename, timestamp, granularity):
        """
        Internal: Calculate rotation-related parameters of the given timestamp
        at the given rotation granularity.
        """
        if granularity == 'X':
            return ('', float('inf'))
        elif granularity == 'Y':
            index = 0
            fmt = '.%Y'
        elif granularity == 'M':
            index = 1
            fmt = '.%Y-%m'
        elif granularity == 'D':
            index = 2
            fmt = '.%Y-%m-%d'
        elif granularity == 'H':
            index = 3
            fmt = '.%Y-%m-%d-%H'
        else:
            raise ValueError('Unrecognized rotation granularity: %s '
                                 '(must be one of X, Y, M, D, H)' %
                             (granularity,))
        fields = time.gmtime(timestamp)
        suffix = time.strftime(fmt, fields)
        expiry_fields = [1970, 1, 1, 0, 0, 0, -1, -1, 0]
        expiry_fields[:index] = fields[:index]
        expiry_fields[index] = fields[index] + 1
        if index == 1 and expiry_fields[1] == 13:
            expiry_fields[0] += 1
            expiry_fields[1] = 1
        filename_fields = os.path.splitext(filename)
        return (filename_fields[0] + suffix + filename_fields[1],
                calendar.timegm(expiry_fields))
    def __init__(self, filename, granularity='X', compression=None,
                 autoflush=True):
        "Instance initializer; see the class docstring for details."
        FileLogHandler.__init__(self, filename, autoflush)
        self.granularity = granularity
        self.compression = self._parse_compression(compression)
        self._cur_params = self._rotation_params(filename,
            os.fstat(self.file.fileno()).st_mtime, self.granularity)
        self._lock = threading.RLock()
    def emit(self, text, timestamp):
        """
        Process the given log message.

        Before doing anything that FileLogHandler would do, this checks if the
        current log file is due being rotated out, and does so if necessary.
        """
        with self._lock:
            if timestamp >= self._cur_params[1]:
                compress_to, compress_using = None, None
                if self.compression is not None:
                    compress_to = '%s.%s' % (self._cur_params[0],
                                             self.compression[0])
                    compress_using = self.compression[1]
                self.rotate(self._cur_params[0], compress_to, compress_using)
                self._cur_params = self._rotation_params(self.file.name,
                                                         timestamp,
                                                         self.granularity)
            FileLogHandler.emit(self, text, timestamp)
    def rotate(self, move_to, compress_to, compress_using):
        """
        Move the current log file to the indicated location and create a new
        log file.
        """
        old_file = self.file
        os.rename(old_file.name, move_to)
        self.file = open(old_file.name, 'a+')
        if compress_to is not None:
            old_file.seek(0)
            with compress_using(compress_to) as drain:
                shutil.copyfileobj(old_file, drain)
            os.remove(move_to)
        old_file.close()
    def read_back(self):
        """
        Read log lines back from the file (and from rotated-out log files)
        and yield them.

        See the base classes' methods for interface details.
        """
        def name_matches(fn):
            if not fn.startswith(prefix) or not fn.endswith(suffix):
                return False
            middle = fn[prefix_end:suffix_start]
            return self.TIME_SUFFIX_RE.match(middle)
        dirname, filename = os.path.split(self.file.name)
        prefix, suffix = os.path.splitext(filename)
        if not dirname:
            dirname = '.'
        if self.compression is not None:
            suffix += '.' + self.compression[0]
            open_file = self.compression[2]
        else:
            open_file = open
        prefix_end = len(prefix)
        suffix_start = -len(suffix) if suffix else None
        rotated_files = sorted(filter(name_matches, os.listdir(dirname)))
        for fn in rotated_files:
            with open_file(os.path.join(dirname, fn)) as fp:
                for line in fp:
                    yield line.rstrip('\n')
        for line in FileLogHandler.read_back(self):
            yield line

class Logger:
    """
    Logger(handler) -> new instance

    A Logger sends timestamp-prefixed messages to a handler or swallows them.

    If handler is None, log() does nothing; otherwise, each logged message is
    prefixed with a timestamp and handed off to the handler for further
    processing (such as writing to a file).

    The key method of this class is log(), with its extension log_exception()
    for providing a standartized and compact response to exceptions.

    Logger instances support the context manager protocol, close()ing
    themselves on __exit__.

    It is encouraged to write logs in a particular machine-readable fashion,
    which is followed by log_exception(). A machine-readable log line consists
    of the following items:

        [<TIMESTAMP>] <TAG> <key-1>=<value-1> <key-2>=<value-2> ...

    <TIMESTAMP> is provided by log(); <TAG> is a (conventionally uppercase)
    word classifying the log line; any amount of key-value pairs (where keys
    should use lowercase names and separate words using dashes) may follow.
    Values should be alike to Python object literals or bare words (see the
    format() method for details). The module-level read_logs() function can
    read back valid lines in this format.

    An instance of this class pre-configured to write to standard output is
    provided as the module-level DEFAULT_LOGGER variable. Similarly, the
    module-level NULL_LOGGER discards all messages.
    """
    @classmethod
    def format(cls, obj):
        """
        Pretty-print the given object in a way suitable for inclusion into a
        machine-readable log line.

        Although this method works on any Python object, only the following
        types (some with special formatting as provided by this method) can be
        read back by read_logs():
        - The constants None, True, False, Ellipsis (represented using the
          given names);
        - Decimal integers (matching the regular expression /[+-]?[0-9]+/);
        - Finite floating-point numbers (matching the regular expression
          /[+-]?[0-9]+(\.[0-9]+)?([eE][+-]?[0-9]+)?/; note that abbreviations
          like 1. or .1 are *not* permitted);
        - Python string literals (an optional "u" prefix is permitted for
          Python 2 compatibility);
        - Tuples or lists of any of the above types (both of these are encoded
          surrounded by parentheses, with the items separated by commata (and
          no spaces), with no trailing comma).
        - Dicts of any of the above types (these are encoded, again, without
          redundant whitespace).
        Note that tuples, lists, and dicts may not be nested (except that
        a tuple/list may be contained immediately inside a dict).

        While format() emits strings in the format described above,
        read_logs() also accepts strings as produced by Python's repr() (which
        include whitespace and potential trailing commata), although the
        format described above is preferred.

        For completeness' sake, in read_logs(), parameter values that do not
        contain any of the forbidden characters matching the character class
        ['"()[\]{},:\s], and are not any of the constant values named above,
        are treated as bare words, i.e. they are decoded to strings without
        further modification. Note that bare words may not be nested inside
        Python-like object literals.
        """
        if isinstance(obj, dict):
            return '{' + ','.join(cls.format(k) + ':' + cls.format(v)
                                  for k, v in obj.items()) + '}'
        elif isinstance(obj, (tuple, list)):
            return '(' + ','.join(map(cls.format, obj)) + ')'
        else:
            return repr(obj)
    def __init__(self, handler):
        "Instance initializer; see the class docstring for details."
        self.handler = handler
    def __enter__(self):
        "Context management support; see the class docstring for details."
        return self
    def __exit__(self, *exc_info):
        "Context management support; see the class docstring for details."
        self.close()
    def log(self, msg, timestamp=None):
        r"""
        Format a logging line containing the given message and write it to
        the underlying stream.

        msg is the message to be written, and should not be empty (for
        aesthetic reasons); it is advisable to format it in the way presented
        in the class docstring. timestamp is the point in time with which the
        message is associated; if None, the current time is used.

        If the underlying stream is None, the formatting and writing is not
        done. Otherwise, after prepending a timestamp, the message is
        formatted into an ASCII-only form (replacing non-ASCII Unicode
        characters with \uXXXX or \UXXXXXXXX escape sequences) the stream is
        always flushed.
        """
        if self.handler is None: return
        if timestamp is None: timestamp = time.time()
        m = '[%s] %s' % (time.strftime('%Y-%m-%d %H:%M:%S',
                                       time.gmtime(timestamp)), msg)
        em = m.encode('ascii', 'backslashreplace').decode('ascii')
        self.handler.emit(em, timestamp)
    def log_exception(self, tag, exc, trailer=None, timestamp=None):
        """
        Log a compact message informing about the given exception.

        tag is an arbitrary keyword to prepend to the exception information;
        exc is the exception object (for machine readability, it is subjected
        to a double repr() since many exceptions provide custom
        representations that are hard to read back consistently); trailer is
        an optional string to append to the log line.

        The log line is formatted in the way presented in the class docstring:
        After the keyword, the exception's representation is given after a
        "reason=" key; just after the exception, one or two samples from the
        stack trace (as retrieved from sys.exc_info() at hopefully
        informative points) are given as "last-frame=" and "cause-frame="
        (where the latter may be omitted if both frames are the same); after
        all that, trailer is appended after a space (if trailer is not None).
        All of that is then passed on to log().
        """
        try:
            # frame is the frame where the exception is caught; cause is the
            # frame where it originated. The former might be more useful in
            # that it points into the user's code (instead of nested
            # libraries).
            frame = tuple(traceback.extract_tb(sys.exc_info()[2], 1)[-1])
            cause = tuple(traceback.extract_tb(sys.exc_info()[2])[-1])
        except:
            frame, cause = None, None
        # The exception is repr()-ed twice, since many of those objects have
        # custom representations, which are not necessarily machine-readable,
        # and str() is hardly appropriate.
        if frame == cause:
            msg = '%s reason=%r last-frame=%s' % (tag, repr(exc),
                                                  self.format(frame))
        else:
            msg = '%s reason=%r last-frame=%s cause-frame=%s' % (tag,
                repr(exc), self.format(frame), self.format(cause))
        if trailer is not None: msg += ' ' + trailer
        self.log(msg, timestamp=timestamp)
    def read_back(self, filt=None):
        """
        Parse machine-readable logs taken from this logger's handler.

        filt is invoked on the tag of every log line; the line is discarded if
        filt returns false; the default is to accept every line.

        If this logger has no handler, this produces an empty iterable.

        See also the read_back() method of the handler for additional notes.
        """
        source = () if self.handler is None else self.handler.read_back()
        for record in read_logs(source, filt):
            yield record
    def close(self):
        """
        Close the logger's underlying handler, if any.
        """
        if self.handler is not None: self.handler.close()

DEFAULT_LOGGER = Logger(StreamLogHandler(sys.stdout, read_stream=sys.stdin))
NULL_LOGGER = Logger(None)

LOGLINE_RE = re.compile(r'^\[([0-9 Z:-]+)\]\s+([A-Z0-9_-]+)(?:\s+(.*))?$')
WHITESPACE_RE = re.compile(r'\s+')
SCALAR_RE = re.compile(r'[^"\'()[\]{},:\s]+|u?"(?:[^"\\]|\\.)*"|'
                       r'u?\'(?:[^\'\\]|\\.)*\'')
COMMA_RE = re.compile(r'\s*,\s*')
TUPLE_ENTRY_RE = re.compile(r'(%s)\s*(,)\s*' % SCALAR_RE.pattern)
    # for colorlogs.py
TUPLE_RE = re.compile(r'\(\s*(?:(?:%s)%s)*(?:(?:%s)\s*)?\)' %
                      (SCALAR_RE.pattern, COMMA_RE.pattern,
                       SCALAR_RE.pattern))
DICT_ENTRY_RE = re.compile(r'(%s|%s)\s*:\s*(%s|%s)' %
                           (SCALAR_RE.pattern, TUPLE_RE.pattern,
                            SCALAR_RE.pattern, TUPLE_RE.pattern))
DICT_RE = re.compile(r'\{\s*(?:%s%s)*(?:%s\s*)?\}' %
                     (DICT_ENTRY_RE.pattern, COMMA_RE.pattern,
                      DICT_ENTRY_RE.pattern))
PARAM_RE = re.compile(r'([a-zA-Z0-9_-]+)=(%s|%s|%s)(?=\s|$)' %
                      (SCALAR_RE.pattern, TUPLE_RE.pattern, DICT_RE.pattern))
INTEGER_RE = re.compile(r'^[+-]?[0-9]+$')
FLOAT_RE = re.compile(r'^[+-]?[0-9]+(\.[0-9]+)?([eE][+-]?[0-9]+)?$')
LOG_CONSTANTS = {'None': None, 'True': True, 'False': False,
                 'Ellipsis': Ellipsis}
def read_logs(src, filt=None):
    """
    Parse machine-readable logs taken from src.

    src is an iterable producing lines (e.g. a file object); filt is invoked
    on the tag of every line before parsing its key-value section to allow
    quickly rejecting irrelevant lines.

    If filt is None, all lines are (fully) processed. If filt returns the
    Ellipsis singleton for a tag, the line's content is not parsed and yielded
    as a single string instead.

    See the Logger class for aid with producing machine-readable logs, as well
    as for documentation on how they are formatted.
    """
    def decode_tuple(val):
        return tuple(ast.literal_eval('[' + val[1:-1] + ']'))
    def decode_dict(val):
        val, idx, ret = val[1:-1], 0, {}
        while 1:
            m = DICT_ENTRY_RE.match(val, idx)
            if not m: break
            idx = m.end()
            rk, rv = m.group(1, 2)
            k = decode_tuple(rk) if rk[0] == '(' else ast.literal_eval(rk)
            v = decode_tuple(rv) if rv[0] == '(' else ast.literal_eval(rv)
            ret[k] = v
            m = COMMA_RE.match(val, idx)
            if not m: break
            idx = m.end()
        if idx != len(val):
            raise RuntimeError('Invalid dictionary literal %r?!' % (val,))
        return ret
    if filt is None: filt = lambda tag: True
    for line in src:
        m = LOGLINE_RE.match(line)
        if not m: continue
        ts, tag, args = m.group(1, 2, 3)
        fr = filt(tag)
        if not fr: continue
        pts = calendar.timegm(time.strptime(ts, '%Y-%m-%d %H:%M:%S'))
        if fr is Ellipsis:
            yield (fr, tag, args)
            continue
        values = {}
        if args is not None:
            idx = 0
            while idx < len(args):
                m = WHITESPACE_RE.match(args, idx)
                if m:
                    idx = m.end()
                    if idx == len(args): break
                m = PARAM_RE.match(args, idx)
                if not m: break
                idx = m.end()
                name, val = m.group(1, 2)
                if val in LOG_CONSTANTS:
                    val = LOG_CONSTANTS[val]
                elif INTEGER_RE.match(val):
                    val = int(val)
                elif FLOAT_RE.match(val):
                    val = float(val)
                elif val[0] in '\'"' or val[:2] in ('u"', "u'"):
                    val = ast.literal_eval(val)
                elif val[0] == '(':
                    val = decode_tuple(val)
                elif val[0] == '{':
                    val = decode_dict(val)
                values[name] = val
            if idx != len(args): continue
        yield (pts, tag, values)

class ArgScanner:
    """
    ArgScanner(args, posmin=None, posmax=None) -> new instance

    A simple command-line argument scanner.

    args is a sequence of "raw" arguments (without the program name); posmin
    and posmax indicate, respectively, the minimum and maximum amount of
    positional arguments this scanner will emit.

    This class is intended to be used via the iterator protocol; iterating
    over an instance produces a stream of 2-tuples; in each of the tuples, the
    first entry is one of the strings "opt" or "arg", indicating whether the
    second value is an option or a positional argument. In the latter case,
    the second value is either a single letter (indicating a short option) or
    a long option preceded by a double dash ("--"). After an option has been
    received, the argument() method can be invoked to pull an argument.

    The "raw" arguments are interpreted as follows:
    - A double dash ("--") engages arguments-only mode (without emitting
      anything), where every following raw argument (including another double
      dash) is interpreted as a positional argument.
    - Otherwise, a raw argument starting with a double dash is interpreted as
      a long option. If the raw argument contains an equals sign ("="), the
      name of the option extends up to the equals sign (exclusively), and an
      "option argument" follows after the equals sign, which must be consumed
      via argument() (or ArgScanner raises an error, deeming the command line
      invalid).
    - Otherwise, a raw argument starting with a dash ("-"), but not consisting
      solely of a dash, is interpreted as a group of short options. Each
      character following the dash is emitted as a single short option; if
      argument() is called after a short option has been emitted and there are
      characters in the raw argument after the character being processed,
      those are interpreted as the option argument instead.
    - Otherwise, the raw argument is interpreted as a positional argument.
    - If argument() is called without an option argument as described above
      being available, the next raw argument is consumed and not interpreted
      in any special way. E.g., if the option --foo takes an argument, the
      command line "--foo -- --bar" does *not* result in --bar being
      interpreted as a positional argument.

    If, when all "raw" arguments have been consumed, there have been fewer
    positional arguments processed than posmin (and posmin is not None), an
    error is raised; if posmax arguments have been emitted and another is
    about to be, an error is raised.

    Aside from the iterator protocol and argument(), this class also provides
    some convenience methods, mostly for raising errors.
    """
    OPT = 'opt'
    ARG = 'arg'
    def __init__(self, args, posmin=None, posmax=None):
        "Instance initializer; see the class docstring for details."
        self.args = args
        self.posmin = posmin
        self.posmax = posmax
        self.iter = None
        self.argiter = None
        self.at_arguments = False
        self.last_option = None
        self.next_arg = None
    def __iter__(self):
        "Iterator protocol support; see the class docstring for details."
        return self
    def __next__(self):
        "Iterator protocol support; see the class docstring for details."
        if self.iter is None: self.iter = self._pairs()
        return next(self.iter)
    def next(self):
        "Alias for __next__()."
        if self.iter is None: self.iter = self._pairs()
        return next(self.iter)
    def close(self):
        """
        Stop accepting arguments.

        If there is any pending option or argument, this raises an error;
        otherwise, nothing happens.
        """
        try:
            next(self)
            self.toomany()
        except StopIteration:
            self.iter = None
    def _pairs(self):
        """
        Internal: Generator method backing the iterator protocol support.
        """
        self.argiter = iter(self.args)
        self.at_arguments = False
        self.last_option = None
        self.next_arg = None
        positional = 0
        for arg in self.argiter:
            if self.at_arguments or not arg.startswith('-') or arg == '-':
                positional += 1
                if self.posmax is not None and positional > self.posmax:
                    self.toomany()
                self.last_option = None
                self.next_arg = None
                yield (self.ARG, arg)
            elif arg == '--':
                self.at_arguments = True
            elif not arg.startswith('--'):
                for n, ch in enumerate(arg[1:], 2):
                    self.last_option = '-' + ch
                    if arg[n:]:
                        self.next_arg = arg[n:]
                        yield (self.OPT, ch)
                        if self.next_arg is None: break
                    else:
                        self.next_arg = None
                        yield (self.OPT, ch)
            else:
                idx = arg.find('=')
                if idx == -1:
                    self.last_option = arg
                    self.next_arg = None
                    yield (self.OPT, arg)
                else:
                    self.last_option = arg[:idx]
                    self.next_arg = arg[idx + 1:]
                    yield (self.OPT, self.last_option)
                    if self.next_arg is not None:
                        self._die_opt('Orphaned argument',
                                      tail=': %r' % (self.next_arg,))
        if self.posmin is not None and positional < self.posmin:
            self.toofew()
    def argument(self, type=None):
        """
        Retrieve an argument, optionally converting it to the given type.

        Note that this method is intended to be used while an iteration over
        this ArgScanner is ongoing but currently suspended (e.g. inside a
        "for" loop iterating over this ArgScanner).
        """
        try:
            if self.next_arg is not None:
                arg = self.next_arg
                self.next_arg = None
            else:
                arg = next(self.argiter)
            if type is not None: arg = type(arg)
            return arg
        except StopIteration:
            self._die_opt('Missing required argument')
        except ValueError as exc:
            self._die_opt('Bad argument %r' % (arg,), tail=': %s' % (exc,))
    def die(self, msg=None):
        """
        Raise a SystemExit exception with the given (optional) message.
        """
        if msg is None: raise SystemExit
        raise SystemExit('ERROR: ' + msg)
    def _die_opt(self, msg, tail=None):
        """
        Internal: Helper method for bailing out of processing an option.

        This constructs a message from msg, the option being processed (if
        any), and tail (if not omitted), and passes that on to die().
        """
        if self.last_option is not None:
            msg += ' for %r' % (self.last_option,)
        if tail is not None:
            msg += tail
        self.die(msg)
    def toomany(self):
        """
        Convenience: Abort with a "Too many arguments" message.
        """
        self.die('Too many arguments')
    def toofew(self):
        """
        Convenience: Abort with a "Too few arguments" message.
        """
        self.die('Too few arguments')
    def unknown(self):
        """
        Convenience: Abort with a message indicating that the current option
        is not recognized.

        If there is no option being processed, this raises a RuntimeError
        instead of the SystemExit from die().
        """
        if self.last_option is None:
            raise RuntimeError('No option to be unknown')
        self.die('Unknown option ' + repr(self.last_option))

def open_file(path, mode, **kwds):
    """
    Helper function for opening files from command-line arguments.

    If path is None or the string "-", a file object wrapping standard input
    or output is opened (depending on the first character of mode), which does
    not close the underlying file descriptor when closed; aside from the
    exception above, all arguments are forwarded to the io.open() function.
    """
    # We use io.open() since it allows using file descriptors in both Py2K
    # and Py3K.
    if path is None or path == '-':
        kwds['closefd'] = False
        if mode[:1] == 'r':
            return io.open(sys.stdin.fileno(), mode, **kwds)
        elif mode[:1] in ('w', 'a'):
            return io.open(sys.stdout.fileno(), mode, **kwds)
        else:
            raise ValueError('Unrecognized open_file() mode: %r' %
                             (mode,))
    else:
        return io.open(path, mode, **kwds)

class OptionParser:
    """
    OptionParser(progname=None) -> new instance

    A declarative command-line argument parser.

    progname is the program name to be used in usage and help displays; if not
    given, "..." is used.

    After an instance is create, options and arguments can be configured via
    the option(), flag(), argument(), etc. methods (see in particular
    help_action() for adding a --help option); after that, parse() can be
    invoked to assimilate arguments, and finally get() to retrieve the
    extracted values.
    """
    Scanner = ArgScanner
    def __init__(self, progname=None):
        "Instance initializer; see the class docstring for details."
        self.progname = progname
        self.description = None
        self.options = collections.OrderedDict()
        self.short_options = collections.OrderedDict()
        self.arguments = []
        self.values = {}
        self.arg_index = 0
    def _add_option(self, opt, kwds):
        "Internal: Commit the given option into internal indexes."
        self.options[opt['option']] = opt
        if opt.get('short'): self.short_options[opt['short']] = opt
    def _set_accum(self, opt, kwds, default=True):
        """
        Internal: Configure the given option's value storing.

        opt is a dictionary describing the option (in an internal format);
        kwds contains keyword arguments given to the function defining the
        option; default tells whether a default value should be transferred
        from kwds to opt.

        The following entries of kwds are interpreted:
        default: If given (and the default argument of this function is true),
                 this configures the default value of the option.
        accum  : If this is a callable, it is an "accumulator" function that
                 is invoked for every appearance of the option on the command
                 line, given two arguments (the current state of the
                 accumulator, initialized as the option's default value (which
                 must be provided in this case, or parsing fails), and the
                 converted value of the latest incarnation of the option), and
                 returns the new value for the option. Otherwise, if this is a
                 truthy value, a default accumulator function is substituted
                 depending on the option's default value: If the default is
                 absent or a Python list, a function that appends to the given
                 list is used; otherwise, a function that invokes the "+="
                 operator on the accumulator is used. Finally, if accum is
                 falsy or absent, no accumulation is done and each appearance
                 of the option overwrites any values stored by previous ones.
        """
        def accum_list(list, item):
            list.append(item)
            return list
        def accum_add(accum, item):
            accum += item
            return accum
        if default and 'default' in kwds:
            opt['default'] = kwds['default']
        if 'accum' not in kwds:
            pass
        elif callable(kwds['accum']):
            opt['accum'] = kwds['accum']
        elif kwds['accum']:
            if 'default' not in opt:
                opt['default'] = []
                opt['accum'] = accum_list
            elif isinstance(opt['default'], list):
                opt['accum'] = accum_list
            else:
                opt['accum'] = accum_add
    def _make_desc(self, opt, name, placeholder):
        """
        Internal: Generate the given option's usage and help information.

        opt is a dictionary describing the option (in an internal format);
        name is the name of the option; placeholder is a placeholder for the
        argument the option takes (if any).
        """
        if name is None:
            if placeholder is None:
                res = '...'
            else:
                res = placeholder
                placeholder = None
        else:
            if opt.get('short'):
                res = '--%s|-%s' % (name, opt['short'])
            else:
                res = '--' + name
        opt['namedesc'] = res
        opt['argdesc'] = placeholder or ''
        if placeholder is not None:
            res += ' ' + placeholder
        if opt.get('accum'):
            res += ' [...]'
        if opt.get('omissible'):
            res = '[%s]' % res
        opt['desc'] = res
    def option(self, name, default=None, type=None, **kwds):
        """
        Declare an option taking an argument.

        The following arguments may be passed (see the function signature for
        which are positional and which are keyword-only):
        name       : The name of the option (without the leading "--").
        short      : A single letter (or character) naming a short equivalent
                     for this option.
        help       : A description of the option for the help listing.
        varname    : At which key to store the converted argument; defaults to
                     name.
        default    : The default value for the argument (note that this does
                     *not* undergo conversion via type).
        required   : Whether the option must be specified. This need not
                     conflict with default (such as for accumulating options).
        type       : A callable that takes a string and converts it to some
                     desired type; defaults to str().
        placeholder: A string to represent the argument in usage listings;
                     defaults to the __name__ attribute of type enclosed in
                     less-then/greater-than signs.
        As default subsequent appearances of the option override earlier ones;
        see _set_accum() for means of doing otherwise.
        """
        if type is None: type = str
        try:
            placeholder = kwds['placeholder']
        except KeyError:
            placeholder = '<%s>' % type.__name__
        opt = {'option': name, 'argument': True, 'convert': type,
               'varname': kwds.get('varname', name), 'default': default,
               'omissible': not kwds.get('required'),
               'help': kwds.get('help'), 'short': kwds.get('short')}
        self._set_accum(opt, kwds)
        self._make_desc(opt, name, placeholder)
        self._add_option(opt, kwds)
    def flag_ex(self, name, value=True, varname=None, **kwds):
        """
        Declare an option taking no argument.

        The following arguments may be passed:
        name    : The name of the flag (without the leading "--").
        short   : A single letter (or character) naming a short equivalent for
                  this flag.
        help    : A description of the flag for the help listing.
        value   : The value to store when the flag is specified.
        varname : At which key to store the value True if the flag is
                  specified; defaults to name.
        default : The default value to use when the flag is not specified. If
                  omitted, nothing is stored when the flag does not appear on
                  the command line; this allows letting a flag set a special
                  value for another option without messing up its default
                  value.
        required: Whether the flag must necessarily be specified. Rarely
                  useful.
        As default subsequent appearances of the flag override earlier ones;
        see _set_accum() for means of doing otherwise.
        """
        opt = {'option': name, 'varname': varname or name, 'value': value,
               'omissible': not kwds.get('required'),
               'help': kwds.get('help'), 'short': kwds.get('short')}
        self._set_accum(opt, kwds)
        self._make_desc(opt, name, None)
        self._add_option(opt, kwds)
    def flag(self, name, **kwds):
        """
        Declare an option taking no argument.

        This is a convenience wrapper around flag_ex() that (always) specifies
        default=False; see flag_ex() for details.
        """
        self.flag_ex(name, default=False, **kwds)
    def action(self, name, function, **kwds):
        """
        Declare an action option.

        Differently to the other options, this one executes the given function
        whenever it is encountered on the command line; see help_action()
        for an example. The following arguments can be passed:
        name    : The name of the flag (without the leading "--").
        short   : A single letter (or character) naming a short equivalent for
                  this flag.
        help    : A description of the flag for the help listing.
        function: A callable to execute when the option is encountered. Takes
                  no arguments and the return value is ignored.
        """
        opt = {'option': name, 'action': function, 'omissible': True,
               'help': kwds.get('help'), 'short': kwds.get('short')}
        self._make_desc(opt, name, None)
        self._add_option(opt, kwds)
    def argument(self, name=None, type=None, **kwds):
        """
        Declare a positional argument.

        The following arguments can be passed:
        name       : The name of the argument (used to store values and to
                     construct the default placeholder).
        help       : A description of the argument for the help listing.
        default    : The default value to use if the argument is not
                     specified. If no default is specified and the argument
                     is not passed, the parser aborts.
        required   : Whether the argument must necessarily be specified.
        type       : A callable that takes a string and converts it to some
                     desired type; defaults to str().
        placeholder: A string to represent the argument in usage listings;
                     defaults to the name parameter of type enclosed in
                     less-then/greater-than signs.
        As default, exactly one value is assigned to the argument; if the
        argument is configured to be accumulating (see _set_accum()), it
        consumes all positional arguments remaining when it is its turn.
        """
        if type is None: type = str
        placeholder = kwds.get('placeholder', '<%s>' % name)
        arg = {'varname': name, 'convert': type, 'help': kwds.get('help'),
               'omissible': not kwds.get('required')}
        self._set_accum(arg, kwds)
        self._make_desc(arg, None, placeholder)
        self.arguments.append(arg)
    def help_action(self, name='help', help='Display help', desc=Ellipsis):
        """
        Declare a option providing a help listing.

        name is the name of the option; help is the help entry for the help
        option itself; desc, if not omitted, allows defining the "description"
        attribute of the parser, which is printed as part of the help listing.
        """
        if desc is not Ellipsis: self.description = desc
        self.action(name, lambda: self.help(0), help=help)
    def usage(self, exit=None, write=True):
        """
        Format a usage message, optionally print it, and optionally exit.

        If exit is not None, it is a status code to exit with (after writing,
        if enabled); if write is True, the usage message is written to
        standard error. If this method returns, the return value is the
        usage text.
        """
        usage = ' '.join(['USAGE:', self.progname or '...'] +
            [opt['desc'] for opt in self.options.values()] +
            [arg['desc'] for arg in self.arguments])
        if write:
            sys.stderr.write(usage + '\n')
            sys.stderr.flush()
        if exit is not None:
            sys.exit(exit)
        return usage
    def help(self, exit=None, write=True):
        """
        Format the help listing, optionally print it, and optionally exit.

        If exit is not None, it is a status code to exit with (after writing,
        if enabled); if write is True, the help listing is written to standard
        error. If this method returns, the return value is the full text of
        the help listing.
        """
        help = [self.usage(write=False)]
        if self.description is not None: help.append(self.description)
        names, seps, params, helps = [], [], [], []
        for item in list(self.options.values()) + self.arguments:
            if not item['help']: continue
            names.append(item['namedesc'])
            seps.append('' if item.get('option') or not item['argdesc']
                           else ':')
            params.append(item['argdesc'])
            helps.append(item['help'])
        mnl = max(len(n) + len(s) for n, s in zip(names, seps))
        sp = ' ' if any(params) else ''
        mpl = max(map(len, params))
        newline = '\n' + ' ' * (mnl + len(sp) + mpl + 2)
        for n, s, p, h in zip(names, seps, params, helps):
            fn = n.ljust(mnl - len(s)) + s
            help.append('%s%s%-*s: %s' % (fn, sp, mpl, p,
                                          h.replace('\n', newline)))
        help = '\n'.join(help)
        if write:
            sys.stderr.write(help + '\n')
            sys.stderr.flush()
        if exit is not None:
            sys.exit(exit)
        return help
    def parse(self, args=None):
        """
        Parse the given arguments, or the program's command line.

        args is the list of arguments to parse; if it is None, sys.argv[1:]
        is used.

        This initializes this instance's internal storage with the declared
        arguments' defaults (as far as that has not happened yet), and parses
        the given args. The parsing may raise a SystemExit exception to
        indicate parsing errors or general program termination (e.g. after
        printing the help listing).
        """
        def process(opt, value=None):
            if opt.get('action'):
                opt['action']()
                return
            sv, varname = self.values, opt['varname']
            if varname not in sv and 'default' in opt:
                sv[varname] = opt['default']
            if value is None:
                try:
                    value = opt['value']
                except KeyError:
                    value = parser.argument(opt.get('convert'))
            else:
                value = opt.get('convert', str)(value)
            if 'accum' in opt:
                sv[varname] = opt['accum'](sv[varname], value)
            else:
                sv[varname] = value
        def process_final(opt):
            if opt.get('action'):
                return True
            sv, varname = self.values, opt['varname']
            if varname in sv:
                return True
            elif 'default' in opt and opt.get('omissible'):
                sv[varname] = opt['default']
                return True
            return False
        if args is None: args = sys.argv[1:]
        parser = self.Scanner(args)
        for tp, arg in parser:
            if tp == 'arg':
                try:
                    desc = self.arguments[self.arg_index]
                    process(desc, arg)
                    if 'accum' not in desc: self.arg_index += 1
                except IndexError:
                    parser.toomany()
            elif arg.startswith('--'):
                try:
                    opt = self.options[arg[2:]]
                except KeyError:
                    parser.unknown()
                process(opt)
            else:
                try:
                    opt = self.short_options[arg]
                except KeyError:
                    parser.unknown()
                process(opt)
        for opt in self.options.values():
            if process_final(opt): continue
            parser.die('Missing required option %r' % ('--' + opt['option']))
        for opt in self.arguments:
            if process_final(opt): continue
            parser.toofew()
    def get(self, *names, **kwds):
        """
        Retrieve the values of the options with the given names.

        If names has only one entry, the value of the named option is returned
        directly (without being enclosed in a singleton tuple); if the
        keyword-only argument force_tuple is provided and a truthy value, this
        behavior is suppressed.
        """
        force_tuple = kwds.get('force_tuple')
        try:
            if len(names) == 1 and not force_tuple:
                n = names[0]
                return self.values[n]
            ret = []
            # n is referenced by the exception handler.
            for n in names:
                ret.append(self.values[n])
            return ret
        except KeyError:
            if n in self.options: n = '--' + n
            raise SystemExit('ERROR: Missing value for %r' % n)

class CmdlineBotBuilder:
    """
    CmdlineBotBuilder(botcls=None, defnick=None, defurl=Ellipsis)
        -> new instance

    A command-line-based builder for Bot instances.

    botcls is the bot class to instantiate; must be a subclass of Bot (or
    anything that takes parameters compatible to the Bot constructor);
    defaults to HookBot. defnick is the default nickname to use; if None
    is passed (as the default is), the bot stays invisible. defurl is the
    default URL to connect to; if not provided, a URL must be specified
    explicitly; some specialized bots (like Scribe) pass an explicit None
    here, allowing to be run without a URL.

    The intended workflow for this class is like this:
    1. Create an instance, providing suitable default configuration.
    2. Invoke the make_parser() method; use the return value to configure
       bot-specific command-line arguments.
    3. Invoke the parse() method.
    4. Use add() and add_args() to prepare bot constructor parameters.
    5. Invoke this instance to invoke the bot constructor.

    Cookie handling note: This configures the resulting Bot instance(s) for
    cookie management depending on the values passed via the command line.
    If cookie management is enabled, the CookieJar's "relaxed" attribute (for
    relaxing the handling of Secure cookie attribute) is set to the
    relaxed_cookies attribute of the CmdlineBotBuilder instance, which
    defaults to the RELAXED_COOKIES class attribute, which in turn defaults
    (for CmdlineBotBuilder) to the same-named module-level constant (as it was
    when the module was created), whose value is (finally) taken from the
    INSTABOT_RELAXED_COOKIES environment variable (if the variable is
    nonempty, the constant is True, otherwise False).
    """
    Parser = OptionParser
    RELAXED_COOKIES = RELAXED_COOKIES
    @classmethod
    def build_logger(cls, logfile, rotate=None):
        """
        Create a Logger from the given command-line configuration.
        """
        if logfile is None:
            return NULL_LOGGER
        elif logfile == '-':
            return DEFAULT_LOGGER
        elif rotate is not None:
            rotate_params = RotatingFileLogHandler.parse_cli_config(rotate)
            return Logger(RotatingLogHandler(logfile, **rotate_params))
        else:
            return Logger(FileLogHandler(logfile))
    def __init__(self, botcls=None, defnick=None, defurl=Ellipsis):
        "Instance initializer; see the class docstring for details."
        if botcls is None: botcls = HookBot
        self.botcls = botcls
        self.defnick = defnick
        self.defurl = defurl
        self.cookies = None
        self.relaxed_cookies = self.RELAXED_COOKIES
        self.args = []
        self.kwds = {}
        self.parser = None
    def make_parser(self, *args, **kwds):
        """
        Create and preliminarily configure the underlying OptionParser.

        All arguments are passed on to the Parser class attribute, which is
        expected to be a subclass of OptionParser (and defaults to
        OptionParser itself), with the exception of the keyword-only desc,
        which is forwarded to the parser's help_action() method instead.
        Returns the parser object for additional configuration.
        """
        desc = kwds.pop('desc', Ellipsis)
        self.parser = self.Parser(*args, **kwds)
        self.parser.help_action(desc=desc)
        self.parser.option('nick', self.defnick,
                           help='Nickname to use')
        self.parser.flag_ex('no-nick', None, 'nick',
                            help='Use no nickname at all')
        self.parser.option('cookies',
                           help='Cookie file (empty string -> memory)')
        self.parser.flag_ex('no-cookies', None, 'cookies',
                            help='Do not save cookies')
        self.parser.option('tls', type=websocket_server.quick.tls_flags,
                           placeholder='<key>=<value>[,...]',
                           varname='ssl_config',
                           help='TLS configuration.')
        self.parser.option('logfile', '-',
                           help='Where to write logs ("-" -> stdout)')
        self.parser.option('logrotate', placeholder='<time>[:<compress>]',
                           help='Enable log rotation and configure it')
        self.parser.flag_ex('no-log', None, 'logfile',
                            help='Disable logging entirely')
        kwargs = {}
        if self.defurl is not Ellipsis: kwargs['default'] = self.defurl
        self.parser.argument('url', help='URL to connect to', **kwargs)
        return self.parser
    def process_args(self):
        """
        Convert parsed command-line arguments into bot constructor parameters.

        The parsed arguments are taken from instance state, and the parameters
        are recorded there.
        """
        c = self.parser.get('cookies')
        if c is None:
            self.cookies = None
            self.kwds.pop('cookies', None)
        elif not c:
            self.cookies = websocket_server.cookies.CookieJar()
            self.cookies.relaxed = self.relaxed_cookies
            self.kwds['cookies'] = self.cookies
        else:
            self.cookies = websocket_server.cookies.LWPCookieJar(c,
                stat.S_IRUSR | stat.S_IWUSR)
            self.cookies.relaxed = self.relaxed_cookies
            self.cookies.load()
            self.kwds['cookies'] = self.cookies
        sc = self.parser.get('ssl_config')
        if sc is None:
            self.kwds.pop('ssl_config', None)
        else:
            self.kwds['ssl_config'] = sc
        try:
            logger = self.build_logger(*self.parser.get('logfile',
                                                        'logrotate'))
        except ValueError as exc:
            raise SystemExit('ERROR: ' + str(exc))
        if logger is NULL_LOGGER:
            self.kwds.pop('logger', None)
        else:
            self.kwds['logger'] = logger
    def parse(self, argv=None):
        """
        Parse the given arguments.

        If argv is omitted, sys.argv[1:] is used.

        This forwards to the underlying OptionParser's parse() method, and
        configures this instance's state depending on the result.
        """
        self.parser.parse(argv)
        self.process_args()
    def add(self, *args, **kwds):
        """
        Store the given arguments for passing to the Bot constructor.

        If this is called multiple times, new positional arguments come after
        positional arguments specified previously, and new keyword arguments
        override keyword arguments specified previously.

        For more control, you can also mutate the "args" and "kwds" instance
        attributes directly.
        """
        self.args.extend(args)
        self.kwds.update(kwds)
    def add_args(self, *names):
        """
        Add the values of the named options to the pending Bot constructor
        arguments.

        E.g., if add_args('foo') is called, the Bot constructor will receive
        the keyword argument foo= with the value of the option --foo (or the
        argument <foo>).
        """
        for n in names:
            self.kwds[n] = self.parser.get(n)
    def get_args(self, *names, **kwds):
        """
        Retrieve the values of the given options.

        This forwards to the underlying OptionParser's get() method.
        """
        return self.parser.get(*names, **kwds)
    def __call__(self, *args, **kwds):
        """
        Invoke the constructor of the underlying Bot class and return the
        result.

        Any arguments are forwarded to the Bot constructor, as if add() had
        been called with them but the changes by it only applied to this
        call.

        The values of the "url" and "nick" options are always passed as the
        first positional arguments, irrespective of the values of the "args"
        attribute (any values from which come after the two named here).
        """
        a = [self.parser.get('url'), self.parser.get('nick')]
        a.extend(self.args)
        a.extend(args)
        k = dict(self.kwds, **kwds)
        return self.botcls(*a, **k)
