#!/usr/bin/env python3
# -*- coding: ascii -*-

"""
A script converting Instant message logs between different formats.
"""

import sys, os
import collections

import instabot
import scribe
import colorlogs, id2time, logdump

FORMAT_EXTENSIONS = {'sqlite': 'db', 'db': 'db', 'log': 'log', 'txt': 'text',
                     None: 'text'}

class OptionError(ValueError): pass

READERS, CONVERTERS, WRITERS = {}, {}, {}

def reader(fmt, res_type=None, options=None, close=None):
    def cb(func):
        READERS[fmt] = (func, res_type, options, close)
        return func
    if res_type is None: res_type = fmt
    if options is None: options = {}
    if close and not callable(close): close = lambda x: x.close()
    return cb
def converter(fmt_from, fmt_to, bounding=False, close=None):
    def cb(func):
        if fmt_from not in CONVERTERS:
            CONVERTERS[fmt_from] = {}
        CONVERTERS[fmt_from][fmt_to] = (func, bounding, close)
        return func
    if close and not callable(close): close = lambda x: x.close()
    return cb
def writer(fmt, exp_type=None, options=None):
    def cb(func):
        WRITERS[fmt] = (func, exp_type, options)
        return func
    if exp_type is None: exp_type = fmt
    if options is None: options = {}
    return cb

def find_converters(fmt_from, fmt_to, need_bounding=False):
    # Breadth-first search to find a shortest path.
    pending = collections.deque((fmt_from,))
    seen = {fmt_from: (None, None, None, None)}
    while pending:
        cur = pending.popleft()
        if cur == fmt_to:
            break
        if cur not in CONVERTERS:
            continue
        for fmt, (cvt, bounding, close) in CONVERTERS[cur].items():
            if fmt in seen: continue
            seen[fmt] = (cur, cvt, bounding, close)
            pending.append(fmt)
    else:
        return (None, False)
    # Gather the elements of the path; check if any of them applies full
    # bounding.
    next_fmt, bounded, result = fmt_to, False, []
    while 1:
        fmt, cvt, bounding, close = seen[next_fmt]
        if fmt is None: break
        result.append((cvt, close, fmt, next_fmt))
        if bounding: bounded = True
        next_fmt = fmt
    # Insert a bounding converter if necessary.
    if need_bounding and not bounded:
        if (fmt_to in CONVERTERS and fmt_to in CONVERTERS[fmt_to] and
                CONVERTERS[fmt_to][fmt_to][1]):
            desc = CONVERTERS[fmt_to][fmt_to]
            index = 0
        else:
            for index, (cvt, fmt, next_fmt) in enumerate(result):
                if fmt not in CONVERTERS[fmt]: continue
                desc = CONVERTERS[fmt][fmt]
                if not desc[1]: continue
                index += 1
                break
            else:
                index = None
        if index is not None:
            result.insert(index, (desc[0], desc[2], None, None))
            bounded = True
    # Emit the converter chain in application order.
    result.reverse()
    return (tuple(item[:2] for item in result), bounded)

def parse_options(values, types, error_label=None):
    error_label = '' if error_label is None else '%s ' % (error_label,)
    result = {}
    for k, v in values.items():
        try:
            desc = types[k]
        except KeyError:
            raise OptionError('Unrecognized %soption %r' % (error_label, k))
        if v is None:
            if len(desc) >= 3:
                result[k] = desc[2]
                continue
            v = ''
        try:
            result[k] = desc[0](v)
        except ValueError as exc:
            raise OptionError('Invalid value %r for %soption %r: %s' %
                              (v, error_label, k, exc))
    for key, desc in types.items():
        if len(desc) >= 2 and key not in result:
            result[key] = desc[1]
    return result

class DummyReadbackLogHandler(instabot.LogHandler):
    def __init__(self, stream):
        self.stream = stream

    def close(self):
        self.stream.close()

    def read_back(self):
        return self.stream

class CloseStack:
    def __init__(self):
        self.pending = []

    def __enter__(self):
        return self

    def __exit__(self, *exc_info):
        self.close()

    def add(self, item, cb):
        if cb is None: return item
        self.pending.append((item, cb))
        return item

    def close(self):
        def handle(index):
            if index == len(pending):
                return
            try:
                handle(index + 1)
            finally:
                record = pending[index]
                record[1](record[0])

        pending, self.pending = self.pending, []
        handle(0)

@reader('log', options={'rotate': (str, None)}, close=True)
def read_log(filename, bounds, options):
    return instabot.CmdlineBotBuilder.build_logger(filename,
                                                   options['rotate'])

@reader('log-color', 'log', close=True)
def read_log_color(filename, bounds, options):
    def stream():
        with instabot.open_file(filename, 'r') as fp:
            for item in colorlogs.unhighlight_stream(fp, True):
                yield item
    return instabot.Logger(DummyReadbackLogHandler(stream()))

@reader('db', close=True)
def read_db(filename, bounds, options):
    if filename == '-':
        raise RuntimeError('Cannot read database from standard input')
    return scribe.LogDBSQLite(filename)

@converter('log', 'db', close=True)
def convert_log_db(logger, bounds):
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
    db.init()
    with logger:
        logs, uuids = scribe.read_posts_ex(logger, db.capacity(),
                                           message_filter)
        db.extend(logs)
        db.extend_uuid(uuids)
    return db

@converter('db', 'messages', True)
def convert_db_messages(db, bounds):
    with db:
        messages = db.query(bounds[0], bounds[1], bounds[2])
        uuids = db.query_uuid(m['from'] for m in messages)
    bounds[:] = [None, None, None]
    return (messages, uuids)

@writer('log', options={'rotate': (str, None)})
def write_log(filename, logger, options):
    with instabot.CmdlineBotBuilder.build_logger(filename,
                                                 options['rotate']) as drain:
        drain.ADJUST_FILE_TIMESTAMPS = True
        for ts, tag, args in logger.read_back(lambda t: Ellipsis):
            drain.log((tag if args is None else '%s %s' % (tag, args)), ts)

@writer('log-color', 'log')
def write_log_color(filename, logger, options):
    with instabot.open_file(filename, 'a') as fp:
        stream = logger.read_back(lambda t: Ellipsis)
        for line in colorlogs.highlight_stream(stream):
            fp.write(line + '\n')

@writer('db', 'messages')
def write_db(filename, data, options):
    if filename == '-':
        raise RuntimeError('Cannot write database to standard output')
    messages, uuids = data
    with scribe.LogDBSQLite(filename) as db:
        db.extend(messages)
        db.extend_uuid(uuids)

@writer('text', 'messages', {'detail': (int, 0),
                             'mono': (bool, False, True)})
def write_text(filename, data, options):
    messages, uuids = data
    messages = logdump.sort_threads(messages)
    fmt = logdump.LogFormatter(detail=options['detail'], mono=options['mono'])
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
    p.option('fmt-from', short='F',
             help='Format of the input data (default: guessed from file '
                  'extension, or an error for standard input)')
    p.option('fmt-to', short='f',
             help='Format of the output (default: guessed from file '
                  'extension, or "text" for standard output)')
    p.option('select', short='s', type=select, accum=True, default=[],
             help='Only process the messages matching the given criterion')
    p.option('output', short='o', default='-',
             help='Where to write the results (- is standard output and the '
                  'default)')
    p.option('opt-from', short='V', type=pair, accum=True, default=[],
             help='Pass generic options to the input reader')
    p.option('opt-to', short='v', type=pair, accum=True, default=[],
             help='Pass generic options to the output writer')
    p.argument('input', default='-',
               help='Where to read data from (- is standard input and the '
                    'default)')
    p.parse(sys.argv[1:])
    fmt_f, fmt_t = p.get('fmt-from', 'fmt-to')
    file_f, file_t = p.get('input', 'output')
    try:
        if fmt_f is None: fmt_f = guess_format(file_f)
        reader, fmt_fr, descs_f, close_reader = READERS[fmt_f]
    except KeyError:
        raise SystemExit('ERROR: Unrecognized --from format: %r' % fmt_f)
    try:
        if fmt_t is None: fmt_t = guess_format(file_t)
        writer, fmt_tr, descs_t = WRITERS[fmt_t]
    except KeyError:
        raise SystemExit('ERROR: Unrecognized --to format: %r' % fmt_f)
    bounds = [None, None, None]
    for i, v in p.get('select'):
        bounds[i] = v
    need_bounds = any(b is not None for b in bounds)
    converters, bounded = find_converters(fmt_fr, fmt_tr, need_bounds)
    if converters is None:
        raise SystemExit('ERROR: Cannot convert from %r to %r' %
                         (fmt_f, fmt_t))
    elif need_bounds and not bounded:
        raise SystemExit('ERROR: Cannot filter messages while converting '
                         'from %r to %r' % (fmt_f, fmt_t))
    try:
        options_f = parse_options(dict(p.get('opt-from')), descs_f,
                                  error_label='input')
        options_t = parse_options(dict(p.get('opt-to')), descs_t,
                                  error_label='output')
    except OptionError as exc:
        raise SystemExit('ERROR: %s' % (exc,))
    with CloseStack() as cs:
        data = cs.add(reader(file_f, bounds, options_f), close_reader)
        for cvt, close in converters:
            data = cs.add(cvt(data, bounds), close)
        if bounds != [None, None, None]:
            raise SystemExit('ERROR: Could not select messages')
        writer(file_t, data, options_t)

if __name__ == '__main__': main()
