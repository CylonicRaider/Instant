#!/usr/bin/env python3
# -*- coding: ascii -*-

# A script dumping Instant message logs in a format similar to the copy-paste
# pretty-printer.

import sys, time
import operator
import instabot, id2time, scribe

# Provide a fancy __name__ for help display.
class msgid(id2time.MessageID): pass

# Return msglist sorted in the order it would be put into by the Web client.
# NOTE that messages' immediate parent might be missing.
def sort_threads(msglist):
    def dump(k):
        if k in index:
            ret.append(index[k])
        ids = [m['id'] for m in children.get(k, ())]
        ids.sort()
        for k in ids:
            dump(k)
    # Put messages into indexes
    index, children = {}, {}
    for m in msglist:
        index[m['id']] = m
        children.setdefault(m.get('parent'), []).append(m)
    roots = [k for k in children if k is not None and
             (k not in index or index[k].get('parent') is None)]
    roots.sort()
    # Extract messages again
    ret = []
    for k in roots: dump(k)
    return ret

class LogFormatter:
    def __init__(self, detail=0, mono=False):
        self.detail = detail
        self.mono = mono

    # Return a plain-text representation of msg.
    # NOTE that there is no trailing newline.
    def format_message(self, msg, indent='', first_indent=None):
        if first_indent is None: first_indent = indent
        prefix, lines = '<%s> ' % msg['nick'], msg['text'].split('\n')
        align = ' ' * len(prefix) if self.mono else '  '
        return '\n'.join((first_indent + prefix if n == 0 else
                          indent + align) + l for n, l in enumerate(lines))

    # Return a plain-text representation of the messages in msglist.
    # NOTE that msglist is used in the order given, and there is no trailing
    #      newline.
    def format_logs(self, msglist, uuids=None):
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
                res.append(prefix(None) + '| ' * len(stack) + '...')
                stack.append(m['parent'])
            p, ind = prefix(m['id']), '| ' * len(stack)
            res.append(self.format_message(m, ' ' * len(p) + ind, p + ind))
            stack.append(m['id'])
        return '\n'.join(res)

def main():
    # Parse command line
    p = instabot.OptionParser(sys.argv[0])
    p.help_action()
    p.option('from', short='f', type=msgid,
             help='Minimal (earliest) message ID to output')
    p.option('to', short='t', type=msgid,
             help='Maximal (latest) message ID to output')
    p.option('length', short='l', type=int,
             help='Maximal amount of messages to output')
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
    # Format messages
    fmt = LogFormatter(detail=p.get('detail'), mono=p.get('mono'))
    print (fmt.format_logs(sort_threads(messages), uuids))

if __name__ == '__main__': main()
