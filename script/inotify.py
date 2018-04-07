
# -*- coding: ascii -*-

"""
Python ctypes wrapper around Linux' inotify API.
"""

import sys, os
import ctypes

__all__ = ['INotify', 'watch']

# Constants
# Taken from glibc as of commit 23158b08a0908f381459f273a984c6fd328363cb. We
# only include the platform-independent flags.
# Supported events suitable for MASK parameter of INOTIFY_ADD_WATCH.
IN_ACCESS        = 0x00000001 # File was accessed.
IN_MODIFY        = 0x00000002 # File was modified.
IN_ATTRIB        = 0x00000004 # Metadata changed.
IN_CLOSE_WRITE   = 0x00000008 # Writtable file was closed.
IN_CLOSE_NOWRITE = 0x00000010 # Unwrittable file closed.
IN_OPEN          = 0x00000020 # File was opened.
IN_MOVED_FROM    = 0x00000040 # File was moved from X.
IN_MOVED_TO      = 0x00000080 # File was moved to Y.
IN_CREATE        = 0x00000100 # Subfile was created.
IN_DELETE        = 0x00000200 # Subfile was deleted.
IN_DELETE_SELF   = 0x00000400 # Self was deleted.
IN_MOVE_SELF     = 0x00000800 # Self was moved.

# Events sent by the kernel.
IN_UNMOUNT    = 0x00002000 # Backing fs was unmounted.
IN_Q_OVERFLOW = 0x00004000 # Event queued overflowed.
IN_IGNORED    = 0x00008000 # File was ignored.

# Helper events.
IN_CLOSE = IN_CLOSE_WRITE | IN_CLOSE_NOWRITE # Close.
IN_MOVE  = IN_MOVED_FROM | IN_MOVED_TO       # Moves.

# Special flags.
IN_ONLYDIR     = 0x01000000 # Only watch the path if it is a directory.
IN_DONT_FOLLOW = 0x02000000 # Do not follow a sym link.
IN_EXCL_UNLINK = 0x04000000 # Exclude events on unlinked objects.
IN_MASK_ADD    = 0x20000000 # Add to the mask of an already existing watch.
IN_ISDIR       = 0x40000000 # Event occurred against dir.
IN_ONESHOT     = 0x80000000 # Only send event once.

# All events which a program can wait on.
IN_ALL_EVENTS = (IN_ACCESS | IN_MODIFY | IN_ATTRIB | IN_CLOSE_WRITE |
                 IN_CLOSE_NOWRITE | IN_OPEN | IN_MOVED_FROM |
                 IN_MOVED_TO | IN_CREATE | IN_DELETE | IN_DELETE_SELF |
                 IN_MOVE_SELF)

# All inotify constants for enumeration and reverse lookup
constants = dict((k, v) for k, v in globals().items() if k.startswith('IN_'))
__all__ += list(constants)

# Own constants
READ_BUFFER_SIZE = 16384

# Structures
class inotify_event(ctypes.Structure):
    _fields_ = (('wd', ctypes.c_int),
                ('mask', ctypes.c_uint32),
                ('cookie', ctypes.c_uint32),
                ('len', ctypes.c_uint32),
                ('__name', ctypes.c_char * 0))

    @classmethod
    def parse_list(cls, data):
        ret, offset, clssize = [], 0, ctypes.sizeof(cls)
        while offset < len(data):
            # Retrieve the "core" structure.
            remaining = len(data) - offset - clssize
            if remaining < 0:
                raise ValueError('inotify_event buffer truncated')
            entry = cls.from_buffer_copy(data, offset)
            offset += clssize
            # Retrieve the flexible array member.
            if remaining < entry.len:
                raise ValueError('inotify_event buffer truncated')
            entry.name = data[offset : offset + entry.len].rstrip(b'\0')
            offset += entry.len
            ret.append(entry)
        return ret

    def __repr__(self):
        try:
            name = repr(self.name)
        except AttributeError:
            name = '(~%r chars)' % (self.len,)
        return '<%s(wd=%r, mask=%r, cookie=%r, path=%r, name=%s)>' % (
            self.__class__.__name__, self.wd, self.mask, self.cookie,
            getattr(self, 'path'), name)

# Load the C functions
try:
    libc = ctypes.CDLL('libc.so.6', use_errno=True)
except OSError:
    raise ImportError('Could not load libc6')

try:
    inotify_init = libc.inotify_init
    inotify_add_watch = libc.inotify_add_watch
    inotify_rm_watch = libc.inotify_rm_watch
except AttributeError:
    raise ImportError('libc6 has no inotify bindings')

# Enforce C function signatures
def check_errno(result, func, args):
    if result == -1:
        code = ctypes.get_errno()
        raise IOError(code, os.strerror(code))
    return result

inotify_init.argtypes = ()
inotify_init.errcheck = check_errno
inotify_add_watch.argtypes = (ctypes.c_int, ctypes.c_char_p, ctypes.c_uint32)
inotify_add_watch.errcheck = check_errno
inotify_rm_watch.argtypes = (ctypes.c_int, ctypes.c_int)
inotify_rm_watch.errcheck = check_errno

# Path encoding facilities
try:
    fsencode = os.fsencode
    fsdecode = os.fsdecode
except AttributeError:
    def fsencode(s):
        if isinstance(s, bytes):
            return s
        return s.encode(sys.getfilesystemencoding(),
                        errors='surrogateescape')
    def fsdecode(s):
        if isinstance(s, str):
            return s
        return s.decode(sys.getfilesystemencoding(),
                        errors='surrogateescape')

# Pythonic wrappers
class INotify:
    """
    INotify() -> new instance

    This class maintains an inotify file descriptor, which is automatically
    created on instance initialization.
    """

    def __init__(self):
        "Initializer; see class docstring for details"
        self.fd = inotify_init()
        self.watches = {}

    def __enter__(self):
        "Context manager entry; see class docstring for details"
        return self

    def __exit__(self, *args):
        "Context manager exit; see class docstring for details"
        self.close()

    def __iter__(self):
        """
        __iter__() -> iterator

        Return an iterator that continuously yields events (whenever they
        appear); see get_events().
        """
        while 1:
            for evt in self.get_events():
                yield evt

    def __del__(self):
        "Destructor; invokes close()."
        self.close()

    def fileno(self):
        """
        fileno() -> int

        Return the inotify file descriptor managed by this instance.
        """
        return self.fd

    def close(self, __os=os):
        """
        close() -> None

        Close the underlying file descriptor (if this has not already
        happened).
        """
        if self.fd is not None:
            __os.close(self.fd)
            self.fd = None
            self.watches.clear()

    def watch(self, path, mask):
        """
        watch(path, mask) -> int

        Watch for events matching mask on path, and return the watch
        descriptor.
        """
        wd = inotify_add_watch(self.fd, fsencode(path), mask)
        self.watches[wd] = path
        return wd

    def unwatch(self, wd):
        """
        unwatch(wd) -> None

        Stop watching on wd.
        """
        self.watches.pop(wd, None)
        inotify_rm_watch(self.fd, wd)

    def get_watch(self, path):
        """
        get_watch(path) -> int

        Return a watch descriptor corresponding to path, or None.
        WARNING: The returned watch descriptor may have already been deleted
                 internally. Prefer using the path attribute of events
                 returned by get_event() instead.
        """
        for wd, p in self.watches.items():
            if p == path: return wd
        raise None

    def get_events(self):
        """
        get_events() -> list

        Retrieve a list of events for the watched paths. Each entry of the
        output has the following attributes:
        wd    : The watch descriptor as an integer. Can be -1 for certain
                ("global") events.
        mask  : A bitmask of the events that happened to the file watched by
                wd.
        cookie: Value for correlating IN_MOVED_FROM and IN_MOVED_TO events.
        path  : The path that was passed to the watch() call that returned
                wd (as a string).
        name  : The name of a directory entry the event happened to, if
                applicable, or None.
        """
        buf = os.read(self.fd, READ_BUFFER_SIZE)
        events = inotify_event.parse_list(buf)
        # Perform bookkeeping
        for evt in events:
            evt.path = self.watches.get(evt.wd)
            if evt.mask & IN_IGNORED:
                self.watches.pop(evt.wd, None)
            evt._name = evt.name
            evt.name = fsdecode(evt.rawname)
        return events

def watch(path, mask):
    """
    watch(path, mask) -> iterator

    Convenience wrapper for watching a single file. This creates an INotify
    instance, registers path to be watched for events matching mask, and
    yields all events read from the INotify instance.
    """
    with INotify() as notifier:
        notifier.watch(path, mask)
        for event in notifier:
            yield event
