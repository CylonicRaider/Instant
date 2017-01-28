#!/usr/bin/env python3
# -*- coding: ascii -*-

import sys

import instabot

NICKNAME = 'Echo'

def post_cb(self, msg, meta):
    if msg['text'].startswith('!echo '):
        return msg['text'][6:]

def main():
    parser = instabot.argparse(sys.argv[1:])
    url, nickname = None, NICKNAME
    for arg in parser:
        if arg == '--help':
            sys.stderr.write('USAGE: %s [--help] [--nick name] url\n' %
                             sys.argv[0])
            sys.exit(0)
        elif arg == '--nick':
            nickname = parser.send('arg')
        elif arg.startswith('-'):
            parser.send('unknown')
        elif url is not None:
            parser.send('toomany')
        else:
            url = arg
    if url is None: raise SystemExit('ERROR: Too few arguments')
    bot = instabot.HookBot(url, nickname, post_cb=post_cb)
    try:
        bot.run()
    except KeyboardInterrupt:
        sys.stderr.write('\n')
    finally:
        bot.close()

if __name__ == '__main__': main()
