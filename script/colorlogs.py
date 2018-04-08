#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Perform syntax highlighting on Scribe logs.

import sys, os, re, io
import time
import errno

import instabot

try:
    import inotify
except ImportError:
    inotify = None

# Allow suppressing backoff if it's faulty.
DO_BACKOFF = (not os.environ.get('COLORLOGS_NO_BACKOFF'))
# How many bytes to scan in one round of "backing off".
BACKOFF_BLOCKSIZE = 16384

# File following poll time.
FOLLOW_POLL_INTERVAL = 1

# Hard-coded ANSI escape sequences for coloring.
COLORS = {None: '\033[0m', 'bold': '\033[1m', 'black': '\033[30m',
    'red': '\033[31m', 'green': '\033[32m', 'orange': '\033[33m',
    'blue': '\033[34m', 'magenta': '\033[35m', 'cyan': '\033[36m',
    'gray': '\033[37m'}

def highlight(line):
    def highlight_scalar(val):
        if val in instabot.CONSTANTS:
            return (COLORS['magenta'], val)
        elif instabot.INTEGER.match(val):
            return (COLORS['cyan'], val)
        else:
            return (COLORS[None], val)
    m = instabot.LOGLINE.match(line)
    if not m: return line
    ret = [line[:m.start(2)], COLORS['bold'], m.group(2), COLORS[None],
           line[m.end(2):m.start(3)]]
    idx = m.start(3)
    if idx != -1:
        while idx < len(line):
            # Skip whitespace
            m = instabot.WHITESPACE.match(line, idx)
            if m:
                ret.extend((COLORS[None], m.group()))
                idx = m.end()
                if idx == len(line): break
            # Match the next parameter; output name
            m = instabot.PARAM.match(line, idx)
            if not m: break
            name, val = m.group(1, 2)
            ret.extend((COLORS['green'], name, '='))
            # Output value
            if val[0] == '(':
                # Output tuple prefix
                ret.extend((COLORS['orange'], '('))
                sm = instabot.WHITESPACE.match(val, 1)
                if sm:
                    ret.extend((COLORS[None], sm.group()))
                    subidx = sm.end()
                else:
                    subidx = 1
                # Output tuple values
                while subidx < len(val):
                    sm = instabot.TUPLE_ENTRY.match(val, subidx)
                    if not sm: break
                    ret.extend(highlight_scalar(sm.group(1)))
                    sp = val[sm.end(1):sm.start(2)]
                    if sp: ret.extend((COLORS[None], sp))
                    ret.extend((COLORS['orange'], ','))
                    sp = val[sm.end(2):sm.end()]
                    if sp: ret.extend((COLORS[None], sp))
                    subidx = sm.end()
                # Output trailing entry
                sm = instabot.SCALAR.match(val, subidx)
                if sm:
                    ret.extend(highlight_scalar(sm.group()))
                    ret.extend((COLORS[None], val[sm.end():-1]))
                else:
                    ret.extend((COLORS[None], val[subidx:-1]))
                # Output suffix
                ret.extend((COLORS['orange'], ')'))
            else:
                ret.extend(highlight_scalar(val))
            idx = m.end()
        ret.extend((COLORS[None], line[idx:]))
    return ''.join(ret)

def highlight_stream(it, newlines=False):
    if not newlines:
        for line in it:
            yield highlight(line)
    else:
        for line in it:
            yield highlight(line.rstrip('\n')) + '\n'

class linecnt(int):
    def __new__(cls, value):
        sup = super(linecnt, cls)
        if isinstance(value, str):
            if not re.match(r'[+-]?[1-9][0-9]*', value):
                raise ValueError('Invalid line count')
            elif value.startswith('+'):
                return sup.__new__(cls, '-' + value[1:])
            elif value.startswith('-'):
                return sup.__new__(cls, value[1:])
        return sup.__new__(cls, value)

def seek(fp, offset, whence):
    try:
        return fp.seek(offset, whence)
    except io.UnsupportedOperation:
        return None
    except IOError as e:
        if e.errno != errno.ESPIPE: raise
        return None

def backoff(infile, lines):
    # Read infile "backwards" until we've encountered no less than lines
    # newline characters.
    # BUG: Might not work properly if the file is truncated concurrently.
    if seek(infile, 0, io.SEEK_END) is None: return False
    while lines > 0:
        # Position the file offset back
        try:
            infile.seek(-BACKOFF_BLOCKSIZE, io.SEEK_CUR)
        except IOError as e:
            if e.errno == errno.EINVAL: break
            raise
        # Scan for newlines
        lines -= infile.read(BACKOFF_BLOCKSIZE).count(b'\n')
        # Compensate for the read just above
        try:
            infile.seek(-BACKOFF_BLOCKSIZE, io.SEEK_CUR)
        except IOError as e:
            if e.errno == errno.EINVAL: break
            raise
    else:
        # Do not seek to the beginning if the loop terminated normally
        return True
    infile.seek(0, io.SEEK_SET)
    return True

def itertail(it, count):
    if count < 0:
        # For negative count, drop some items and then output everything.
        for i in range(-count - 1): next(it)
        for item in it: yield item
    elif count == 0:
        # For a count of zero, drop all input and output zero items.
        for item in it: pass
    else:
        # Otherwise, maintain a ring buffer of count items.
        buf, offset = [None] * count, 0
        for item in it:
            buf[offset] = item
            offset = (offset + 1) % count
        # If the item at offset is None, the buffer was not filled;
        # otherwise, the first item is at offset. Cut-and-paste buffer
        # to become linear.
        if buf[offset] is None:
            buf = buf[:offset]
        else:
            buf = buf[offset:] + buf[:offset]
        # Drain buffer.
        for item in buf:
            yield item

def follow(fp, path=None):
    # Keep track of file truncation (shared by both backends).
    def watch_truncate(fp):
        def callback():
            stats = os.fstat(fp.fileno())
            offset = fp.tell()
            if offset > stats.st_size:
                sys.stderr.write('----- file truncated -----\n')
                sys.stderr.flush()
                fp.seek(stats.st_size)
                return True
            return False
        if seek(fp, 0, io.SEEK_CUR) is None: return None
        return callback
    # Regularly poll the file for new lines.
    def follow_poll(fp):
        watcher = watch_truncate(fp)
        while 1:
            for l in fp:
                yield l
            if watcher is not None and watcher():
                continue
            time.sleep(FOLLOW_POLL_INTERVAL)
    # Use inotify to get quicker updates when the file changes.
    def follow_inotify(fp, path):
        watcher = watch_truncate(fp)
        with inotify.INotify() as notifier:
            notifier.watch(path, inotify.IN_MODIFY)
            while 1:
                for l in fp:
                    yield l
                notifier.get_events()
                if watcher is not None:
                    watcher()
    # Choose a backend and return it.
    if path is None or inotify is None:
        return follow_poll(fp)
    else:
        return follow_inotify(fp, path)

def open_file(path, mode):
    if path == '-':
        if mode[:1] in ('a', 'w'):
            return io.open(sys.stdout.fileno(), mode)
        else:
            return io.open(sys.stdin.fileno(), mode)
    else:
        return io.open(path, mode)

def main():
    p = instabot.OptionParser(sys.argv[0])
    p.help_action(desc='A syntax highlighter for Scribe logs.')
    p.option('out', short='o', default='-',
             help='File to write output to (- is standard output and '
                 'the default)')
    p.flag('append', short='a',
           help='Append to output file instead of overwriting it')
    p.option('lines', short='n', type=linecnt, default=-1,
             help='Only output trailing lines\n'
                 'N, -N -> Output the last N lines\n'
                 '+N -> Output from the (one-based) N-th line on')
    p.flag('follow', short='f',
           help='Keep waiting for input on EOF')
    p.argument('in', default='-',
               help='File to read from (- is standard input and '
                   'the default)')
    p.parse(sys.argv[1:])
    inpath, outpath, append = p.get('in', 'out', 'append')
    lines, do_follow = p.get('lines', 'follow')
    do_backoff = (DO_BACKOFF and lines > 0)
    inm, outm = ('rb' if do_backoff else 'r'), ('a' if append else 'w')
    with open_file(inpath, inm) as fi, open_file(outpath, outm) as fo:
        if do_backoff:
            backoff(fi, lines)
            it = io.TextIOWrapper(fi)
        else:
            it = fi
        for l in highlight_stream(itertail(it, lines), True):
            fo.write(l)
        if do_follow:
            fo.flush()
            if inpath == '-': inpath = None
            for l in highlight_stream(follow(it, inpath), True):
                fo.write(l)
                fo.flush()

if __name__ == '__main__': main()
