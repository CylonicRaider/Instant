#!/usr/bin/env python3
# -*- coding: ascii -*-

"""
A script converting Instant message ID-s from and to timestamps.
"""

import sys, re, time, calendar
import collections
import instabot

__all__ = ['MessageID', 'id2time', 'time2id', 'parse_time', 'parse',
           'format_id', 'format_time']

class MessageID(collections.namedtuple('_MesageID', 'sec ms seq')):
    """
    An Instant message ID in broken-up form

    The fields are:
    sec: Seconds (arbitrary integer; should be in the [-9007199254740,
         9007199254739], i.e. [-2**63/1024000, 2**63/1024000-1], range
         for maximum interoperability).
    ms : Milliseconds (integer in the [0, 999] range).
    seq: Sequence number (integer in the [0, 1023] range).
    """

    def __new__(cls, *params):
        """
        Construct a new instance

        If only one parameter is given, it must be a string, integer,
        or MessageID instance; it is processed using parse(),
        id2time(), or its contents are copied into the new instance.
        With three parameters, the sec, ms, and seq fields are assigned
        directly from the parameters.
        NOTE that passing a base specification along with strings is
             not available, since bases other than 16 are considered
             non-standard.
        """
        sup = super(MessageID, cls)
        if len(params) == 1:
            if isinstance(params[0], MessageID):
                return sup.__new__(cls, *params[0])
            elif isinstance(params[0], str):
                return sup.__new__(cls, *parse(params[0]))
            else:
                return sup.__new__(cls, *time2id(params[0]))
        else:
            return sup.__new__(cls, *params)

    def __int__(self):
        """
        Return the numerical representation of this ID
        """
        return time2id(self)

    def format_id(self):
        """
        Return the canonical hexadecimal representation of this ID
        """
        return format_id(time2id(self))

    def format_time(self, compact=False):
        """
        Return a human-readable string representation of this ID

        The compact argument is passed through to format_time().
        """
        return format_time(self, compact)

def id2time(ident):
    """
    Split a message identifier into its semantic parts

    ident is an integer; the return value is a MessageID instance.
    """
    ts = ident >> 10
    return MessageID(ts // 1000, ts % 1000, ident & 0x3FF)

def time2id(parts):
    """
    Combine a broken-up message identifier into a numeric one

    parts is a MessageID instance (or any 3-sequence); the return
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

def format_id(ident):
    """
    Convert a message ID to its canonical string form
    """
    return '%016X' % ident

def format_time(parts, compact=False):
    """
    Format a split-up message ID to a timestamp string

    If compact is true, an alternate slightly shorter representation
    without whitespace is emitted. Both compact and "long" forms have
    a fixed width, and are recognized by parse_time().
    """
    sec, ms, seq = parts
    if compact:
        return '%s.%03d#%04d' % (time.strftime('%Y-%m-%d_%H:%M:%S',
            time.gmtime(sec)), ms, seq)
    else:
        return '%s.%03d Z, #%04d' % (time.strftime('%Y-%m-%d %H:%M:%S',
            time.gmtime(sec)), ms, seq)

def read_stdin():
    for line in sys.stdin:
        for word in line.split():
            yield word

def main():
    def parse_cmdline(ident, base):
        try:
            return parse(ident, base)
        except ValueError as e:
            raise SystemExit('ERROR: ' + str(e))
    p = instabot.OptionParser(sys.argv[0])
    p.help_action(desc='Convert Instant message ID-s to/from timestamps.')
    p.flag('decimal', short='d',
           help='Use nonstandard decimal ID-s instead of hex ones')
    p.flag('compact', short='c',
           help='Use compact format for timestamps')
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
            parts = parse_cmdline(i, base)
            print (fmt % (i, time2id(parts)))
    else:
        compact = p.get('compact')
        for i in values:
            parts = parse_cmdline(i, base)
            print ('%s: %s' % (i, format_time(parts, compact)))

if __name__ == '__main__': main()
