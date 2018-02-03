#!/usr/bin/env python3
# -*- coding: ascii -*-

import sys, time
import instabot

def id2time(ident):
    ts = ident >> 10
    # Seconds, milliseconds, sequence
    return (ts / 1000, ts % 1000, ident & 0x3FF)

def read_stdin():
    for line in sys.stdin:
        for word in line.split():
            yield word

def main():
    p = instabot.OptionParser(sys.argv[0])
    p.help_action()
    p.flag('decimal', short='d', help='Use decimal ID-s instead of hex ones')
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
    for i in values:
        sec, ms, seq = id2time(int(i, base))
        timestr = time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime(sec))
        print ('%s: %s.%03d Z, #%d' % (i, timestr, ms, seq))

if __name__ == '__main__': main()
