#!/usr/bin/env python3
# -*- coding: ascii -*-

import sys

import instabot

NICKNAME = 'Echo'

def post_cb(self, msg, meta):
    if msg['text'].startswith('!echo '):
        return msg['text'][6:]

def main():
    p = instabot.OptionParser(sys.argv[0])
    p.help_action()
    p.option('nick', NICKNAME, help='The nickname to use')
    p.argument('url', help='The URL to connect to')
    p.parse(sys.argv[1:])
    url, nickname = p.get('url', 'nick')
    bot = instabot.HookBot(url, nickname, post_cb=post_cb)
    try:
        bot.run()
    except KeyboardInterrupt:
        sys.stderr.write('\n')
    finally:
        bot.close()

if __name__ == '__main__': main()
