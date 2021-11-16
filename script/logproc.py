#!/usr/bin/env python3
# -*- coding: ascii -*-

"""
A script converting Instant message logs between different formats.
"""

import sys, os
import collections

import instabot
import scribe
import id2time, logdump

FORMAT_EXTENSIONS = {'sqlite': 'db', 'db': 'db', 'log': 'log', 'txt': 'text',
                     None: 'text'}

class OptionError(Exception): pass

READERS, CONVERTERS, WRITERS = {}, {}, {}

def reader(fmt):
    def cb(func):
        READERS[fmt] = func
        return func
    return cb
def converter(fmt_from, fmt_to):
    def cb(func):
        if fmt_from not in CONVERTERS:
            CONVERTERS[fmt_from] = {}
        CONVERTERS[fmt_from][fmt_to] = func
        return func
    return cb
def writer(fmt, option_descs=None):
    def cb(func):
        WRITERS[fmt] = (func, option_descs)
        return func
    if option_descs is None: option_descs = {}
    return cb

def find_converters(fmt_from, fmt_to):
    if fmt_from == fmt_to: return ()
    if fmt_from not in CONVERTERS: return None
    if fmt_to in CONVERTERS[fmt_from]: return (CONVERTERS[fmt_from][fmt_to],)
    # Breadth-first search to find a shortest path.
    pending, seen = collections.deque((fmt_from,)), {fmt_from: (None, None)}
    while pending:
        cur = pending.popleft()
        if cur not in CONVERTERS:
            continue
        for fmt, cvt in CONVERTERS[cur]:
            if fmt in seen: continue
            seen[fmt] = (cur, cvt)
            pending.append(fmt)
            if fmt != fmt_to: continue
            result = []
            while 1:
                fmt, cvt = seen[fmt]
                if fmt is None: break
                result.append(cvt)
            result.reverse()
            return tuple(result)
    return None

def parse_options(values, types):
    seen, result = set(), {}
    for k, v in values.items():
        try:
            desc = types[k]
        except KeyError:
            raise OptionError('Unrecognized option %r' % (key,))
        seen.add(k)
        if v is None:
            if len(desc) >= 3:
                result[k] = desc[2]
                continue
            v = ''
        try:
            result[k] = desc[0](v)
        except ValueError as exc:
            raise OptionError('Invalid value %r for option %r: %s' %
                              (v, k, exc))
    for key, desc in types.items():
        if len(desc) >= 2 and key not in seen:
            result[key] = desc[1]
    return result

@reader('log')
def read_scribe(filename, bounds):
    if bounds[0] is None and bounds[1] is None:
        message_filter, db_capacity = None, None
    elif bounds[0] is None:
        message_filter = lambda m: m['id'] <= bounds[1]
        db_capacity = bounds[2]
    elif bounds[1] is None:
        message_filter = lambda m: m['id'] >= bounds[0]
        db_capacity = None
    else:
        message_filter = lambda m: bounds[0] <= m['id'] <= bounds[1]
        db_capacity = bounds[2]
    db = scribe.LogDBList(db_capacity)
    with instabot.CmdlineBotBuilder.build_logger(filename) as l:
        logs, uuids = scribe.read_posts_ex(l, db.capacity(),
                                           message_filter)
        db.extend(logs)
        db.extend_uuid(uuids)
    return db

@reader('db')
def read_db(filename, bounds):
    if filename == '-':
        raise RuntimeError('Cannot read database from standard input')
    return scribe.LogDBSQLite(filename)

@writer('db')
def write_db(filename, data, options):
    if filename == '-':
        raise RuntimeError('Cannot write database to standard output')
    messages, uuids = data
    db = scribe.LogDBSQLite(filename)
    db.init()
    try:
        db.extend(messages)
        db.extend_uuid(uuids)
    finally:
        db.close()

@writer('text', {'detail': (int, 0), 'monospaced': (bool, False)})
def write_text(filename, data, options):
    messages, uuids = data
    messages = logdump.sort_threads(messages)
    fmt = logdump.LogFormatter(detail=options['detail'],
                               mono=options['monospaced'])
    with instabot.open_file(filename, 'w') as fp:
        fmt.format_logs_to(fp, messages, uuids)

def main():
    def pair(text):
        key, sep, value = text.partition('=')
        return (key, None) if not sep else (key, value)

    def select(text):
        key, sep, value = text.partition('=')
        if not sep:
            raise ValueError('Invalid selection: %r' % (text,))
        elif key in ('from', 'to'):
            reference = id2time.parse(value)
            return ((0 if key == 'from' else 1), reference.format_id())
        elif key == 'count':
            count = int(value)
            return (2, count)
        else:
            raise ValueError('Invalid selection key: %r' % (key,))

    def guess_format(filename):
        if filename == '-':
            return FORMAT_EXTENSIONS[None]
        stem, ext = os.path.splitext(filename)
        if not ext:
            return FORMAT_EXTENSIONS[None]
        try:
            return FORMAT_EXTENSIONS[ext.replace('.', '')]
        except KeyError:
            raise SystemExit('ERROR: Cannot guess format of file %r' %
                             (filename,))

    p = instabot.OptionParser(sys.argv[0])
    p.help_action(desc='Interconvert various Instant message log formats.')
    p.option('from', short='f',
             help='Format of the input data (default: guessed from file '
                  'extension, or an error for standard input)')
    p.option('to', short='t',
             help='Format of the output (default: guessed from file '
                  'extension, or "text" for standard output)')
    p.option('select', short='s', type=select, accum=True, default=[],
             help='Only process the messages matching the given criterion')
    p.option('output', short='o', default='-',
             help='Where to write the results (- is standard output and the '
                  'default)')
    p.option('option', short='v', type=pair, accum=True, default=[],
             help='Pass generic options to the output writer')
    p.argument('input', default='-',
               help='Where to read data from (- is standard input and the '
                    'default)')
    p.parse(sys.argv[1:])
    fmt_f, fmt_t, file_f, file_t = p.get('from', 'to', 'input', 'output')
    try:
        if fmt_f is None: fmt_f = guess_format(file_f)
        reader = READERS[fmt_f]
    except KeyError:
        raise SystemExit('ERROR: Unrecognized --from format: %r' % fmt_f)
    try:
        if fmt_t is None: fmt_t = guess_format(file_t)
        writer, option_descs = WRITERS[fmt_t]
    except KeyError:
        raise SystemExit('ERROR: Unrecognized --to format: %r' % fmt_f)
    try:
        options = parse_options(dict(p.get('option')), option_descs)
    except OptionError as exc:
        raise SystemExit('ERROR: %s' % (exc,))
    bounds = [None, None, None]
    for i, v in p.get('select'):
        bounds[i] = v
    db = reader(file_f, bounds)
    with db:
        messages = db.query(bounds[0], bounds[1], bounds[2])
        uuids = db.query_uuid(m['from'] for m in messages)
    writer(file_t, (messages, uuids), options)

if __name__ == '__main__': main()
