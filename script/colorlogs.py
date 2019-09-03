#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Perform syntax highlighting on Scribe logs.
"""

import sys, os, re
import time
import errno

import instabot

# Hard-coded ANSI escape sequences for coloring.
COLORS = {None: '\033[0m', 'bold': '\033[1m', 'black': '\033[30m',
    'red': '\033[31m', 'green': '\033[32m', 'orange': '\033[33m',
    'blue': '\033[34m', 'magenta': '\033[35m', 'cyan': '\033[36m',
    'gray': '\033[37m'}

def highlight(line, filt=None):
    def highlight_scalar(val):
        if val in instabot.CONSTANTS:
            return (COLORS['magenta'], val)
        elif instabot.INTEGER.match(val):
            return (COLORS['cyan'], val)
        else:
            return (COLORS[None], val)
    m = instabot.LOGLINE.match(line)
    if not m: return line
    if filt and not filt(m.group(2)): return None
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

def highlight_stream(it, newlines=False, filt=None):
    if not newlines:
        for line in it:
            hl = highlight(line, filt)
            if hl is not None: yield hl
    else:
        for line in it:
            hl = highlight(line.rstrip('\n'), filt)
            if hl is not None: yield hl + '\n'

def main():
    p = instabot.OptionParser(sys.argv[0])
    p.help_action(desc='A syntax highlighter for Scribe logs.')
    p.option('exclude', short='x', default=[], accum=True,
             help='Filter out lines of this type (may be repeated)')
    p.option('out', short='o',
             help='File to write output to (- is standard output and '
                 'the default)')
    p.flag_ex('append', short='a', varname='outmode', value='a', default='w',
              help='Append to output file instead of overwriting it')
    p.flag('line-buffered', short='u',
           help='Flush output after each input line')
    p.argument('in', default='-',
               help='File to read from (- is standard input and '
                   'the default)')
    p.parse(sys.argv[1:])
    ignore, inpath, outpath = p.get('exclude', 'in', 'out')
    outmode, linebuf = p.get('outmode', 'line-buffered')
    try:
        filt = (lambda t: t not in ignore) if ignore else None
        of = instabot.OptionParser.open_file
        with of(inpath, 'r') as fi, of(outpath, outmode) as fo:
            for l in highlight_stream(fi, True, filt):
                fo.write(l)
                if linebuf: fo.flush()
    except KeyboardInterrupt:
        # Suppress noisy stack traces.
        pass

if __name__ == '__main__': main()
