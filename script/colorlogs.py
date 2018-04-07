#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Perform syntax highlighting on Scribe logs.

import sys, os, re

import instabot

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

def open_file(path, mode):
    if path == '-':
        if mode[:1] in ('a', 'w'):
            return os.fdopen(sys.stdout.fileno(), mode, 1)
        else:
            return os.fdopen(sys.stdin.fileno(), mode, 1)
    else:
        return open(path, mode)

def main():
    p = instabot.OptionParser(sys.argv[0])
    p.help_action(desc='A syntax highlighter for Scribe logs.')
    p.option('out', short='o', default='-',
             help='File to write output to (- is standard output and '
                 'the default)')
    p.flag('append', short='a',
           help='Append to output file instead of overwriting it')
    p.option('lines', short='n', type=linecnt, default=None,
             help='Only output trailing lines.\n'
                 'N, -N -> Output the last N lines.\n'
                 '+N -> Output from the (one-based) N-th line on.')
    p.argument('in', default='-',
               help='File to read from (- is standard input and '
                   'the default)')
    p.parse(sys.argv[1:])
    inpath, outpath, append = p.get('in', 'out', 'append')
    lastlines = p.get('lines')
    outmode = 'a' if append else 'w'
    with open_file(inpath, 'r') as fi, open_file(outpath, outmode) as fo:
        it = iter(fi)
        if lastlines is not None: it = itertail(it, lastlines)
        for l in highlight_stream(it, True):
            fo.write(l)

if __name__ == '__main__': main()
