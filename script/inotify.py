
# -*- coding: ascii -*-

# Python ctypes wrapper around Linux' inotify API.

import sys, os
import ctypes

# Define structures
class inotify_event(ctypes.Structure):
    _fields_ = (('wd', ctypes.c_int),
                ('mask', ctypes.c_uint32),
                ('cookie', ctypes.c_uint32),
                ('len', ctypes.c_uint32),
                ('name', ctypes.c_char * 0))

# Load the C functions
try:
    libc = ctypes.CDLL('libc.so.6', use_errno=True)
except OSError:
    raise ImportError('Could not load libc6 -- is this a Linux system?')

try:
    inotify_init = libc.inotify_init
    inotify_add_watch = libc.inotify_add_watch
    inotify_rm_watch = libc.inotify_rm_watch
except AttributeError:
    raise ImportError('libc6 has no inotify bindings')

try:
    inotify_init1 = libc.inotify_init1
except AttributeError:
    inotify_init1 = None

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
if inotify_init1 is not None:
    inotify_init1.argtypes = (ctypes.c_int,)
    inotify_init1.errcheck = check_errno
