#!/usr/bin/env python3
# -*- coding: ascii -*-

"""
A script dumping Instant message logs in a format similar to the copy-paste
pretty-printer.
"""

import sys, time
import operator

import instabot, id2time, scribe

# Return msglist sorted in the order it would be put into by the Web client.
# NOTE that messages' immediate parents might be missing.
def sort_threads(msglist):
    def dump(k):
        if k in index:
            ret.append(index[k])
        for c in sorted(children.get(k, ())):
            dump(c)
    # Put messages into indexes
    index, children = {}, {}
    for m in msglist:
        index[m['id']] = m
        p = m.get('parent')
        children.setdefault(p, []).append(m['id'])
    # Compute set of roots
    roots = set(children)
    roots.difference_update(index)
    roots.update(children.get(None, ()))
    roots.discard(None)
    # Extract messages again
    ret = []
    for k in sorted(roots): dump(k)
    return ret

class LogFormatter:
    def __init__(self, detail=0, mono=False):
        self.detail = detail
        self.mono = mono

    # Return a plain-text representation of msg.
    # There is no trailing newline.
    def format_message(self, msg, indent='', first_indent=None):
        if first_indent is None: first_indent = indent
        # Historical data have null nicks in some instances.
        prefix = '<%s> ' % (msg['nick'] or '')
        lines = msg['text'].split('\n')
        align = ' ' * len(prefix) if self.mono else '  '
        return '\n'.join((first_indent + prefix if n == 0 else
                          indent + align) + l for n, l in enumerate(lines))

    # Return an iterable formatting messages from msglist.
    # msglist is used exactly in the order given; the individual strings do
    # not include trailing newlines.
    def format_logs_stream(self, msglist, uuids=None):
        def prefix(msgid):
            if self.detail >= 3:
                if msgid is None:
                    return ('[---------- ------------    -----|'
                        '------------------------------------] ')
                else:
                    return '[%s|%36s] ' % (
                        id2time.MessageID(msgid).format_time(),
                        uuids.get(m['from'], m['from']))
            elif self.detail == 2:
                if msgid is None:
                    return '[---------- ------------    -----] '
                else:
                    return '[%s] ' % id2time.MessageID(msgid).format_time()
            elif self.detail == 1:
                if msgid is None:
                    return '[---------- --------  ] '
                else:
                    return time.strftime('[%Y-%m-%d %H:%M:%S Z] ',
                        time.gmtime(int(msgid, 16) // 1024000))
            else:
                return ''
        if uuids is None: uuids = {}
        stack, res = [], []
        for m in msglist:
            while stack and stack[-1] != m.get('parent'): stack.pop()
            if m.get('parent') is not None and not stack:
                yield prefix(None) + '| ' * len(stack) + '...'
                stack.append(m['parent'])
            p, ind = prefix(m['id']), '| ' * len(stack)
            yield self.format_message(m, ' ' * len(p) + ind, p + ind)
            stack.append(m['id'])

    # Return a plain-text representation of the messages in msglist.
    # msglist is used exactly in the order given; there is no trailing
    # newline.
    def format_logs(self, msglist, uuids=None):
        return '\n'.join(self.format_logs_stream(msglist, uuids))

    # Write formatted message logs to the given stream.
    # Each message (including the last one) is written as a full line.
    def format_logs_to(self, stream, msglist, uuids=None):
        for msg in self.format_logs_stream(msglist, uuids):
            stream.write(msg + '\n')

# Provide a fancy __name__ for help display.
def msgid(text):
    return id2time.MessageID(text)

def main():
    # Parse command line
    p = instabot.OptionParser(sys.argv[0])
    p.help_action(desc='Pretty-print contents of Scribe database files.')
    p.option('from', short='f', type=msgid,
             help='Minimal (earliest) message ID to output')
    p.option('to', short='t', type=msgid,
             help='Maximal (latest) message ID to output')
    p.option('length', short='l', type=int,
             help='Maximal amount of messages to output')
    p.option('output', short='o',
             help='Path of file to write to (defaults to standard output)')
    p.flag_ex('append', short='a', varname='outmode', value='a', default='w',
              help='Append to output file instead of overwriting it')
    p.flag_ex('detail', short='d', default=0, value=1, accum=True,
              help='Make output more detailed')
    p.flag('mono', short='m',
           help='Optimize indents for monospaced display')
    p.argument('msgdb', help='Database file to dump logs from')
    p.parse(sys.argv[1:])
    # Retrieve messages
    lfrom, lto, amount = p.get('from', 'to', 'length')
    if lfrom is not None: lfrom = lfrom.format_id()
    if lto is not None: lto = lto.format_id()
    db = scribe.LogDBSQLite(p.get('msgdb'))
    db.init()
    try:
        messages = db.query(lfrom, lto, amount)
        uuids = db.query_uuid(m['from'] for m in messages)
    finally:
        db.close()
    # Sort messages
    # This step cannot be streamed as someone could reply to an arbitrarily
    # old messages; since the database API does not include querying by
    # parent (yet), we also cannot offload that to the DB.
    messages = sort_threads(messages)
    # Format messages
    fmt = LogFormatter(detail=p.get('detail'), mono=p.get('mono'))
    output, outmode = p.get('output', 'outmode')
    with instabot.open_file(output, outmode) as outfile:
        fmt.format_logs_to(outfile, messages, uuids)

if __name__ == '__main__': main()
