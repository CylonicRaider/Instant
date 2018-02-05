#!/usr/bin/env python3
# -*- coding: ascii -*-

# A small example bot for Instant.

import sys

import instabot

NICKNAME = 'Echo'

def post_cb(self, msg, meta):
    if msg['text'].startswith('!echo '):
        return msg['text'][6:]

def main():
    b = instabot.CmdlineBotBuilder(defnick=NICKNAME)
    b.make_parser(sys.argv[0])
    b.parse(sys.argv[1:])
    bot = b(post_cb=post_cb)
    try:
        bot.run()
    except KeyboardInterrupt:
        sys.stderr.write('\n')
    finally:
        bot.close()

if __name__ == '__main__': main()
