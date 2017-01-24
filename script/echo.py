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
    try:
        it = iter(sys.argv[1:])
        for arg in it:
            if arg == '--help':
                sys.stderr.write('USAGE: %s [--help] [--nick name] url\n' %
                                 sys.argv[0])
                sys.exit(0)
            elif arg == '--nick':
                nickname = next(it)
            elif arg.startswith('-'):
                sys.stderr.write('ERROR: Unknown option %s!\n' % arg)
                sys.exit(1)
            elif url is not None:
                sys.stderr.write('ERROR: Too many URL-s passed!\n')
                sys.exit(1)
            else:
                url = arg
    except StopIteration:
        sys.stderr.write('ERROR: Missing required argument for %s!\n' %
                         arg)
        sys.exit(1)
    bot = instabot.HookBot(url, nickname, post_cb=post_cb)
    bot.run()

if __name__ == '__main__': main()
