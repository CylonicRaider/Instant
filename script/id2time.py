#!/usr/bin/env python3
# -*- coding: ascii -*-

# A script converting Instant message ID-s from and to timestamps.

import sys, re, time, calendar
import collections
import instabot

__all__ = ['MessageID', 'id2time', 'time2id', 'parse_time', 'parse']

# Fields are: seconds (arbitrary integer), milliseconds (integer in [0, 999]
# range), sequence number (integer in [0, 1023] range).
MessageID = collections.namedtuple('MessageID', 'sec ms seq')

def id2time(ident):
    """
    Split a message identifier into its semantic parts

    ident is an integer; the return value is a MessageID instance.
    """
    ts = ident >> 10
    return MessageID(ts / 1000, ts % 1000, ident & 0x3FF)

def time2id(parts):
    """
    Combine a split-up message identifier into a single identifier

    parts is a MessageID structure (or any 3-sequence); the return
    value is an integer.
    """
    ts, ms, seq = parts
    return ts * 1024000 + ms * 1024 + seq

def parse_time(s):
    """
    Parse a string representation of an ID to a MessageID instance

    The format of accepted strings is somewhat lenient, but includes
    strings corresponding to the following pattern:

        YYYY-MM-DD [HH:MM:SS[.FFF]][ Z][, #IIII]

    ...where brackets denote optional omission, and the letters have
    the following meanings: Y - year; M - month (in date) / minute (in
    time); D - day; H - hour; S - second; F - milliseconds; Z - the
    letter "Z" or the string "UTC" (the timezone is always UTC
    regardless of the presence or absence of this); I - sequence
    identifier. Numeric fields may have any amount of digits, except for
    milliseconds, which must have three (if present).
    """
    m = re.match(r'(?i)^\s*(?P<Y>\d+)-(?P<m>\d+)-(?P<d>\d+)'
        r'((\s+|[_T])(?P<H>\d+):(?P<M>\d+):(?P<S>\d+)(\.(?P<f>\d{3}))?)?'
        r'(\s*(Z|UTC))?(\s*(,\s*)?#\s*(?P<i>\d+))?\s*$', s)
    if not m: raise ValueError('Invalid date-time: %r' % (s,))
    tt = m.group('Y', 'm', 'd', 'H', 'M', 'S')
    if tt[4] is None: tt = tt[:3] + (0, 0, 0)
    ts = calendar.timegm([int(i) for i in tt + (0, 0, 0)])
    ms = int(m.group('f'), 10) if m.group('f') else 0
    seq = int(m.group('i'), 10) if m.group('i') else 0
    return MessageID(ts, ms, seq)

def parse(s, base=16):
    """
    Parse a free-form input string to a MessageID instance

    The input string is either a numerical message ID string or a
    date-time string as recognized by parse_time().
    In order to correctly parse the canonical hexadecimal ID format,
    base must be 16; in any case, it is used as the explicit base for
    integer parsing (and can be 0 for flexible-base input).
    """
    try:
        return id2time(int(s, base))
    except ValueError:
        try:
            return parse_time(s)
        except ValueError:
            raise ValueError('Cannot parse message ID: %r' % (s,))

def read_stdin():
    for line in sys.stdin:
        for word in line.split():
            yield word

def main():
    p = instabot.OptionParser(sys.argv[0])
    p.help_action()
    p.flag('decimal', short='d',
           help='Use nonstandard decimal ID-s instead of hex ones')
    p.flag('reverse', short='r',
           help='Output ID-s instead of timestamps')
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
            ident = time2id(parse(i, base))
            print (fmt % (i, ident))
    else:
        for i in values:
            sec, ms, seq = parse(i, base)
            timestr = time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime(sec))
            print ('%s: %s.%03d Z, #%04d' % (i, timestr, ms, seq))

if __name__ == '__main__': main()
