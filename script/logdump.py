#!/usr/bin/env python3
# -*- coding: ascii -*-

# A script dumping Instant message logs in a format similar to the copy-paste
# pretty-printer.

import sys
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

# Return a plain-text representation of msg.
# NOTE that there is no trailing newline.
def format_message(msg, indent='', mono=False):
    prefix, lines = '<%s> ' % msg['nick'], msg['text'].split('\n')
    align = ' ' * len(prefix) if mono else '  '
    res = [(prefix if n == 0 else align) + l for n, l in enumerate(lines)]
    return '\n'.join(indent + l for l in res)

# Return a plain-text representation of the messages in msglist.
# NOTE that msglist is used in the order given, and there is no trailing
#      newline.
def format_logs(msglist, mono=False):
    stack, res = [], []
    for m in msglist:
        while stack and stack[-1] != m.get('parent'): stack.pop()
        stack.append(m['id'])
        res.append(format_message(m, '| ' * (len(stack) - 1), mono))
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
    finally:
        db.close()
    # Format messages
    print (format_logs(sort_threads(messages), p.get('mono')))

if __name__ == '__main__': main()
