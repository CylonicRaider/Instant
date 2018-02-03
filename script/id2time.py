#!/usr/bin/env python3
# -*- coding: ascii -*-

import sys, time

def id2time(ident):
    ts = ident >> 10
    # Seconds, milliseconds, sequence
    return (ts / 1000, ts % 1000, ident & 0x3FF)

def main():
    if len(sys.argv) == 1:
        sys.stderr.write('USAGE: %s ID [ID ...]\n' % sys.argv[0])
        sys.stderr.flush()
        sys.exit(0)
    for i in sys.argv[1:]:
        sec, ms, seq = id2time(int(i, 16))
        timestr = time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime(sec))
        print ('%s: %s.%s Z, #%s' % (i, timestr, ms, seq))

if __name__ == '__main__': main()
