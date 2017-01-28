#!/usr/bin/env python3
# -*- coding: ascii -*-

import sys

import instabot

NICKNAME = 'Echo'

def post_cb(self, msg, meta):
    if msg['text'].startswith('!echo '):
        return msg['text'][6:]

def main():
    url, nickname = None, NICKNAME
    parser = instabot.ArgParser(sys.argv[1:])
    for tp, arg in parser.pairs(1, 1):
        if tp == 'arg':
            url = arg
        elif arg == '--help':
            sys.stderr.write('USAGE: %s [--help] [--nick name] url\n' %
                             sys.argv[0])
            sys.exit(0)
        elif arg == '--nick':
            nickname = parser.argument()
        else:
            parser.unknown()
    bot = instabot.HookBot(url, nickname, post_cb=post_cb)
    try:
        bot.run()
    except KeyboardInterrupt:
        sys.stderr.write('\n')
    finally:
        bot.close()

if __name__ == '__main__': main()
