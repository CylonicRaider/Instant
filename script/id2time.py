#!/usr/bin/env python3
# -*- coding: ascii -*-

# A script converting Instant message ID-s from and to timestamps.

import sys, re, time, calendar
import instabot

def id2time(ident):
    ts = ident >> 10
    # Seconds, milliseconds, sequence
    return (ts / 1000, ts % 1000, ident & 0x3FF)

def time2id(ts, ms, seq):
    return ts * 1024000 + ms * 1024 + seq

def parse_time(s):
    m = re.match(r'(?i)^\s*(?P<Y>\d+)-(?P<m>\d+)-(?P<d>\d+)'
        r'((\s+|[_T])(?P<H>\d+):(?P<M>\d+):(?P<S>\d+)(\.(?P<f>\d{3}))?)?'
        r'(\s*(Z|UTC))?(\s*(,\s*)?#\s*(?P<i>\d+))?\s*$', s)
    if not m: raise ValueError('Invalid date-time: %r' % (s,))
    tt = m.group('Y', 'm', 'd', 'H', 'M', 'S')
    if tt[4] is None: tt = tt[:3] + (0, 0, 0)
    ts = calendar.timegm([int(i) for i in tt + (0, 0, 0)])
    ms = int(m.group('f'), 10) if m.group('f') else 0
    seq = int(m.group('i'), 10) if m.group('i') else 0
    return (ts, ms, seq)

def read_stdin():
    for line in sys.stdin:
        for word in line.split():
            yield word

def main():
    p = instabot.OptionParser(sys.argv[0])
    p.help_action()
    p.flag('decimal', short='d', help='Use decimal ID-s instead of hex ones')
    p.flag('reverse', short='r', help='Convert from timestamps to ID-s')
    p.flag('stdin', short='i', help='Read values from stdin')
    p.argument('value', accum=True, help='Values to convert')
    p.parse(sys.argv[1:])
    base = 0 if p.get('decimal') else 16
    if p.get('stdin'):
        if p.get('value'):
            raise SystemExit('ERROR: Too many arguments')
        values = read_stdin()
    else:
        values = p.get('value')
    if p.get('reverse'):
        fmt = '%s: %016X' if base == 16 else '%s: %d'
        for i in values:
            ident = time2id(*parse_time(i))
            print (fmt % (i, ident))
    else:
        for i in values:
            sec, ms, seq = id2time(int(i, base))
            timestr = time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime(sec))
            print ('%s: %s.%03d Z, #%d' % (i, timestr, ms, seq))

if __name__ == '__main__': main()
