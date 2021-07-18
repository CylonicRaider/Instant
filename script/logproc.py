#!/usr/bin/env python3
# -*- coding: ascii -*-

"""
A script converting Instant message logs between different formats.
"""

import sys

import instabot
import scribe
import logdump

READERS, WRITERS = {}, {}

def reader(fmt):
    def cb(func):
        READERS[fmt] = func
        return func
    return cb
def writer(fmt):
    def cb(func):
        WRITERS[fmt] = func
        return func
    return cb

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
    messages = db.query(bounds[0], bounds[1], bounds[2])
    uuids = db.query_uuid(m['from'] for m in messages)
    return messages, uuids

@reader('db')
def read_db(filename, bounds):
    if filename == '-':
        raise RuntimeError('Cannot read database from standard input')
    db = scribe.LogDBSQLite(filename)
    db.init()
    try:
        messages = db.query(bounds[0], bounds[1], bounds[2])
        uuids = db.query_uuid(m['from'] for m in messages)
    finally:
        db.close()
    return messages, uuids

@writer('db')
def write_db(filename, messages, uuids):
    if filename == '-':
        raise RuntimeError('Cannot write database to standard output')
    db = scribe.LogDBSQLite(filename)
    db.init()
    try:
        db.extend(messages)
        db.extend_uuid(uuids)
    finally:
        db.close()

@writer('text')
def write_text(filename, messages, uuids):
    messages = logdump.sort_threads(messages)
    fmt = logdump.LogFormatter()
    with instabot.open_file(filename, 'w') as fp:
        fmt.format_logs_to(fp, messages, uuids)

def main():
    p = instabot.OptionParser(sys.argv[0])
    p.help_action(desc='Interconvert various Instant message log formats.')
    p.option('from', short='f', required=True,
             help='Format of the input data')
    p.option('to', short='t', required=True,
             help='Format of the output')
    p.option('output', short='o', default='-',
             help='Where to write the results (- is standard output and the '
                  'default)')
    p.argument('input', default='-',
               help='Where to read data from (- is standard input and the '
                    'default)')
    p.parse(sys.argv[1:])
    fmt_f, fmt_t, file_f, file_t = p.get('from', 'to', 'input', 'output')
    try:
        reader = READERS[fmt_f]
    except KeyError:
        raise SystemExit('ERROR: Unrecognized --from format: %r' % fmt_f)
    try:
        writer = WRITERS[fmt_t]
    except KeyError:
        raise SystemExit('ERROR: Unrecognized --to format: %r' % fmt_f)
    messages, uuids = reader(file_f, (None, None, None))
    writer(file_t, messages, uuids)

if __name__ == '__main__': main()
